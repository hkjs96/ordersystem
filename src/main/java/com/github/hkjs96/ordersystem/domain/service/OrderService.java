package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.adapter.out.OrderRepositoryPort;
import com.github.hkjs96.ordersystem.adapter.out.event.DomainEventPublisher;
import com.github.hkjs96.ordersystem.domain.entity.Order;
import com.github.hkjs96.ordersystem.domain.event.OrderCancelledEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.domain.repository.OrderRepository;
import com.github.hkjs96.ordersystem.dto.request.OrderRequest;
import com.github.hkjs96.ordersystem.dto.response.OrderResponse;
import com.github.hkjs96.ordersystem.exception.PaymentException;
import com.github.hkjs96.ordersystem.exception.ShipmentException;
import com.github.hkjs96.ordersystem.port.in.OrderUseCase;
import com.github.hkjs96.ordersystem.port.out.InventoryRepositoryPort;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService implements OrderUseCase {
    private final OrderRepositoryPort orderRepositoryPort;
    private final InventoryRepositoryPort inventoryPort;
    private final PublishEventPort eventPort;
    private final DeliveryService deliveryService;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(OrderRequest req) {
        // 1) 재고 확인
        if (!inventoryPort.isStockAvailable(req.productId(), req.quantity())) {
            throw new IllegalStateException("재고 부족: productId=" + req.productId());
        }
        // 2) 주문 생성
        Order order = Order.builder()
                .productId(req.productId())
                .quantity(req.quantity())
                .status(OrderStatus.CREATED)
                .build();
        order = orderRepositoryPort.save(order);

        // 3) 이벤트 발행
        eventPort.publishOrderEvent(new OrderEvent(order.getId(), OrderStatus.CREATED));

        return toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));

        // 재고 복원을 위한 도메인 이벤트 발행
        domainEventPublisher.publish(new OrderCancelledEvent(
                orderId, order.getProductId(), order.getQuantity()
        ));
        order.changeStatus(OrderStatus.CANCELLED);

        // 이벤트 발행
        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.CANCELLED));
    }

    @Transactional(rollbackFor = Exception.class)
    public void prepareShipment(Long orderId) {
        // 중복 로직 제거하고 배송 책임을 DeliveryService에 위임
        deliveryService.initiateShipment(orderId);
    }


    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getProductId(), o.getQuantity(), o.getStatus());
    }
}
