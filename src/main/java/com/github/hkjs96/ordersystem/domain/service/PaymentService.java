package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.adapter.out.OrderRepositoryPort;
import com.github.hkjs96.ordersystem.adapter.out.cache.InventoryRepository;
import com.github.hkjs96.ordersystem.domain.entity.Payment;
import com.github.hkjs96.ordersystem.domain.entity.Order;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.domain.repository.PaymentRepository;
import com.github.hkjs96.ordersystem.exception.PaymentException;
import com.github.hkjs96.ordersystem.port.in.PaymentUseCase;
import com.github.hkjs96.ordersystem.port.out.InventoryRepositoryPort;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final OrderRepositoryPort orderRepositoryPort;
    private final PublishEventPort eventPort;
    private final InventoryRepositoryPort inventoryPort;

    @Override
    @Transactional
    public void initiatePayment(Long orderId) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));
        order.changeStatus(OrderStatus.PAYMENT_REQUESTED);

        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.PAYMENT_REQUESTED));
        // 실제 PG 연동 로직은 비동기로 처리

        log.info("결제 요청 시작: orderId={}", orderId);
    }

    @Override
    @Transactional
    public void completePayment(Long orderId, boolean success) {
        log.info("결제 완료 처리: orderId={}, success={}", orderId, success);

        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));
        // 결제 엔티티 저장
        Payment payment = Payment.builder()
                .orderId(orderId)
                .success(success)
                .transactionId(UUID.randomUUID().toString())
                .build();
        paymentRepository.save(payment);

        // 주문 상태 변경
        if (success) {
            // 🔧 결제 성공 시 DB에 실제 재고 차감
            try {
                // InventoryRepository의 confirmSale 메서드 호출
                if (inventoryPort instanceof InventoryRepository inventoryRepository) {
                    inventoryRepository.confirmSale(order.getProductId(), order.getQuantity(), orderId);
                }

                order.changeStatus(OrderStatus.PAYMENT_COMPLETED);
                eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.PAYMENT_COMPLETED));

                log.info("결제 성공 및 재고 차감 완료: orderId={}, productId={}, quantity={}",
                        orderId, order.getProductId(), order.getQuantity());

            } catch (Exception e) {
                log.error("재고 차감 실패: orderId={}", orderId, e);

                // 결제는 성공했지만 재고 차감 실패
                order.changeStatus(OrderStatus.PAYMENT_COMPLETED); // 결제 성공 상태 유지
                eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.PAYMENT_COMPLETED));

                // TODO: 재고 차감 실패 알림 또는 수동 처리 큐에 추가
                log.warn("수동 재고 처리 필요: orderId={}, productId={}, quantity={}",
                        orderId, order.getProductId(), order.getQuantity());
            }

        } else {
            // 🔧 결제 실패 시 Redis 재고 복원
            inventoryPort.releaseStock(order.getProductId(), order.getQuantity());

            order.changeStatus(OrderStatus.PAYMENT_FAILED);
            eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.PAYMENT_FAILED));

            log.info("결제 실패 및 재고 복원 완료: orderId={}", orderId);
            throw new PaymentException("결제 실패: orderId=" + orderId);
        }
    }
}
