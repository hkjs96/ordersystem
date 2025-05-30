package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.adapter.out.OrderRepositoryPort;
import com.github.hkjs96.ordersystem.domain.entity.Payment;
import com.github.hkjs96.ordersystem.domain.entity.Order;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.domain.repository.PaymentRepository;
import com.github.hkjs96.ordersystem.exception.PaymentException;
import com.github.hkjs96.ordersystem.port.in.PaymentUseCase;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final OrderRepositoryPort orderRepositoryPort;
    private final PublishEventPort eventPort;

    @Override
    @Transactional
    public void initiatePayment(Long orderId) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));
        order.changeStatus(OrderStatus.PAYMENT_REQUESTED);
        orderRepositoryPort.save(order);
        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.PAYMENT_REQUESTED));
        // 실제 PG 연동 로직은 비동기로 처리
    }

    @Override
    @Transactional
    public void completePayment(Long orderId, boolean success) {
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
        OrderStatus newStatus = success ? OrderStatus.PAYMENT_COMPLETED : OrderStatus.PAYMENT_FAILED;
        order.changeStatus(newStatus);
        orderRepositoryPort.save(order);
        eventPort.publishOrderEvent(new OrderEvent(orderId, newStatus));
        if (!success) {
            throw new PaymentException("결제 실패: orderId=" + orderId);
        }
    }
}
