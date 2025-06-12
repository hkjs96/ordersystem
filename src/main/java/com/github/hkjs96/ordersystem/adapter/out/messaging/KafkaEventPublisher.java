package com.github.hkjs96.ordersystem.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaEventPublisher implements PublishEventPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,  // 🔧 추가된 부분
            @Value("${ordersystem.kafka.topics.order-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;  // 🔧 추가된 부분
        this.topic = topic;
    }

    @Override
    public void publishOrderEvent(OrderEvent event) {
        try {
            String key = String.valueOf(event.orderId());
            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(topic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka 전송 실패: orderId={}, error={}",
                                    event.orderId(), ex.getMessage());
                            // TODO: DLQ 전송 또는 재시도 로직
                        } else {
                            log.debug("Kafka 전송 성공: orderId={}", event.orderId());
                        }
                    });

        } catch (JsonProcessingException e) {
            log.error("JSON 직렬화 실패: event={}", event, e);
            throw new RuntimeException("이벤트 발행 실패", e);
        }
    }
}
