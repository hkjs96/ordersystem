package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.domain.entity.Order;
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
    private final OrderRepository orderRepository;
    private final InventoryRepositoryPort inventoryPort;
    private final PublishEventPort eventPort;
    private final PaymentService paymentService;
    private final DeliveryService deliveryService;

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
        order = orderRepository.save(order);
        eventPort.publishOrderEvent(new OrderEvent(order.getId(), OrderStatus.CREATED));

        // 3) 재고 예약
        inventoryPort.reserveStock(req.productId(), req.quantity());

        // 4) 결제 처리 (예외 시 PAYMENT_FAILED 상태로 전이)
        try {
            paymentService.processPayment(order.getId());
        } catch (Exception ex) {
            order.changeStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);
            eventPort.publishOrderEvent(new OrderEvent(order.getId(), order.getStatus()));
            throw new PaymentException("결제 실패: orderId=" + order.getId(), ex);
        }

        // 5) 배송 시작 (예외 시 SHIPMENT_PREPARING → CANCELLED 전이)
        try {
            deliveryService.initiateShipment(order.getId());
        } catch (Exception ex) {
            order.changeStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            eventPort.publishOrderEvent(new OrderEvent(order.getId(), order.getStatus()));
            throw new ShipmentException("배송 시작 실패: orderId=" + order.getId(), ex);
        }

        return toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));
        order.changeStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.CANCELLED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void prepareShipment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));

        // ① JPA 영속성 컨텍스트에 로드된 Order 객체의 상태만 변경해도
        //    flush 시점에 자동으로 UPDATE 쿼리가 나갑니다.
        //    따라서 아래 save() 호출은 사실 불필요합니다.
        order.changeStatus(OrderStatus.SHIPMENT_PREPARING);

        // orderRepository.save(order);  ← 제거 가능

        // ② 트랜잭션 커밋 후에만 이벤트를 내보내야
        //    롤백 시 이벤트 중복/잘못 발행을 방지할 수 있습니다.
        //    Spring 의 TransactionSynchronizationManager 를 활용하거나,
        //    도메인 이벤트 퍼블리셔(예: ApplicationEventPublisher + @TransactionalEventListener) 와 결합하세요.
        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.SHIPMENT_PREPARING));
    }


    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getProductId(), o.getQuantity(), o.getStatus());
    }
}
