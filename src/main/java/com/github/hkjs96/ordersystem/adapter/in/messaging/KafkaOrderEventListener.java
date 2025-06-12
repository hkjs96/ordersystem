package com.github.hkjs96.ordersystem.adapter.in.messaging;

import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.port.in.DeliveryUseCase;
import com.github.hkjs96.ordersystem.port.in.OrderUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOrderEventListener {

    private final OrderUseCase orderUseCase;
    private final ObjectMapper objectMapper;
    private final DeliveryUseCase deliveryUseCase;

    @KafkaListener(
            topics = "${ordersystem.kafka.topics:order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(String message) {
        try {
            // JSON → OrderEvent 파싱
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            // 상태에 따라 UseCase 호출
            switch (event.status()) {
                case PAYMENT_COMPLETED ->
                        deliveryUseCase.initiateShipment(event.orderId());
                case SHIPMENT_PREPARING ->
                        deliveryUseCase.ship(event.orderId());
                // case SHIPPED 등 추가 분기 필요 시 여기에…
                default ->
                        log.info("처리 대상 아님, 상태={}", event.status());
        }
            // 필요한 추가 분기(FAILED, SHIPPED 등) 구현
        } catch (Exception e) {
            log.error("Kafka 이벤트 처리 실패: {}", message, e);
            // TODO: DLQ 전송 또는 재시도 로직 추가 가능
        }
    }
}
