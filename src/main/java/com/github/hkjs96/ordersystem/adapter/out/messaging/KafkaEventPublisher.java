package com.github.hkjs96.ordersystem.adapter.out.messaging;

import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements PublishEventPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${ordersystem.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publishOrderEvent(OrderEvent event) {
        // key: orderId, value: JSON 직렬화
        String key = String.valueOf(event.orderId());
        String payload = String.format(
                "{\"orderId\":%d,\"status\":\"%s\",\"timestamp\":\"%s\"}",
                event.orderId(), event.status(), event.timestamp()
        );
        kafkaTemplate.send(topic, key, payload);
    }
}
