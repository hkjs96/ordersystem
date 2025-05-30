package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.adapter.out.OrderRepositoryPort;
import com.github.hkjs96.ordersystem.adapter.out.event.DomainEventPublisher;
import com.github.hkjs96.ordersystem.domain.entity.Order;
import com.github.hkjs96.ordersystem.domain.event.OrderCancelledEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.dto.request.OrderRequest;
import com.github.hkjs96.ordersystem.dto.response.OrderResponse;
import com.github.hkjs96.ordersystem.port.in.OrderUseCase;
import com.github.hkjs96.ordersystem.port.out.InventoryRepositoryPort;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService implements OrderUseCase {
    private final OrderRepositoryPort orderRepositoryPort;
    private final InventoryRepositoryPort inventoryPort;
    private final PublishEventPort eventPort;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(OrderRequest req) {
        log.info("주문 생성 시작: productId={}, quantity={}", req.productId(), req.quantity());
        // 1) 재고 확인
        if (!inventoryPort.isStockAvailable(req.productId(), req.quantity())) {
            throw new IllegalStateException("재고 부족: productId=" + req.productId());
        }

        // 🔧 재고 예약 처리 활성화
        inventoryPort.reserveStock(req.productId(), req.quantity());

        // 2) 주문 생성
        Order order = Order.builder()
                .productId(req.productId())
                .quantity(req.quantity())
                .status(OrderStatus.CREATED)
                .build();
        order = orderRepositoryPort.save(order);

        // 3) 이벤트 발행
        eventPort.publishOrderEvent(new OrderEvent(order.getId(), OrderStatus.CREATED));

        log.info("주문 생성 완료: orderId={}, productId={}, quantity={}, 재고 예약됨",
                order.getId(), req.productId(), req.quantity());

        return new OrderResponse(order.getId(), order.getProductId(), order.getQuantity(), order.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        log.info("주문 취소 시작: orderId={}", orderId);

        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));

        // 재고 복원을 위한 도메인 이벤트 발행
        domainEventPublisher.publish(new OrderCancelledEvent(
                orderId, order.getProductId(), order.getQuantity()
        ));
        order.changeStatus(OrderStatus.CANCELLED);

        // 이벤트 발행
        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.CANCELLED));

        log.info("주문 취소 완료: orderId={}, productId={}, quantity={}, 재고 복원 요청됨",
                orderId, order.getProductId(), order.getQuantity());
    }
}
