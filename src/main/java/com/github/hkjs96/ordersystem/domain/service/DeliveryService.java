package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.domain.entity.Delivery;
import com.github.hkjs96.ordersystem.domain.entity.Order;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.domain.repository.DeliveryRepository;
import com.github.hkjs96.ordersystem.domain.repository.OrderRepository;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final PublishEventPort eventPort;

    @Transactional(rollbackFor = Exception.class)
    public void initiateShipment(Long orderId) {
        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .build();
        deliveryRepository.save(delivery);

        // 주문 상태 변경
        Order order = orderRepository.findById(orderId).get();
        order.changeStatus(OrderStatus.SHIPMENT_PREPARING);
        orderRepository.save(order);

        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.SHIPMENT_PREPARING));
    }
}
