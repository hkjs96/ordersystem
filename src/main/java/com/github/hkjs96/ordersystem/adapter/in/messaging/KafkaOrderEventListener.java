package com.github.hkjs96.ordersystem.adapter.in.messaging;

import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.port.in.OrderUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class KafkaOrderEventListener {

    private final OrderUseCase orderUseCase;
    private final ObjectMapper objectMapper;

    public KafkaOrderEventListener(OrderUseCase orderUseCase,
                                   ObjectMapper objectMapper) {
        this.orderUseCase = orderUseCase;
        this.objectMapper = objectMapper;  // Spring Boot가 JSR-310 모듈을 등록한 빈
    }

    @KafkaListener(topics = "${ordersystem.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String message) {
        try {
            // JSON → OrderEvent 파싱
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            // 상태에 따라 UseCase 호출
            if (event.status() == OrderStatus.PAYMENT_COMPLETED) {
                orderUseCase.prepareShipment(event.orderId());
            }
            // 필요한 추가 분기(FAILED, SHIPPED 등) 구현
        } catch (Exception e) {
            // 예외 처리 로깅
            throw new RuntimeException("Event 처리 실패: " + message, e);
        }
    }
}
