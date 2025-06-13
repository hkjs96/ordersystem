package com.github.hkjs96.ordersystem.adapter.out.messaging;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private KafkaEventPublisher publisher;

    private final String topic = "orders-events";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // topic 은 생성자 인자로 주입되어야 하므로 직접 설정
        publisher = new KafkaEventPublisher(kafkaTemplate, objectMapper, topic);
    }

    @Test
    @DisplayName("publishOrderEvent 시 KafkaTemplate.send 호출")
    void publishOrderEvent_sendsToKafka() {
        OrderEvent event = new OrderEvent(42L,
                com.github.hkjs96.ordersystem.domain.model.OrderStatus.CREATED,
                Instant.parse("2025-05-19T10:15:30Z"));

        publisher.publishOrderEvent(event);

        String expectedKey = "42";
        String expectedPayload = String.format(
                "{\"orderId\":%d,\"status\":\"%s\",\"timestamp\":\"%s\"}",
                42, "CREATED", "2025-05-19T10:15:30Z"
        );

        verify(kafkaTemplate).send(topic, expectedKey, expectedPayload);
    }
}
