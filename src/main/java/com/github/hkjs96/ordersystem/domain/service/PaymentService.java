package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.domain.entity.Payment;
import com.github.hkjs96.ordersystem.domain.entity.Order;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.domain.repository.PaymentRepository;
import com.github.hkjs96.ordersystem.domain.repository.OrderRepository;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PublishEventPort eventPort;

    @Transactional(rollbackFor = Exception.class)
    public void processPayment(Long orderId) {
        // (실제 결제 게이트웨이 호출 로직 대체 가능)
        boolean success = true;
        Payment payment = Payment.builder()
                .orderId(orderId)
                .success(success)
                .transactionId(UUID.randomUUID().toString())
                .build();
        paymentRepository.save(payment);

        // 주문 상태 변경
        Order order = orderRepository.findById(orderId).get();
        order.changeStatus(success ? OrderStatus.PAYMENT_COMPLETED : OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);

        eventPort.publishOrderEvent(new OrderEvent(orderId, order.getStatus()));
    }
}
