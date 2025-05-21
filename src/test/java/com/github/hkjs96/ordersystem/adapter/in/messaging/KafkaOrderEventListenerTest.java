package com.github.hkjs96.ordersystem.adapter.in.messaging;

import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.port.in.OrderUseCase;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@EmbeddedKafka(
        partitions = 1,
        topics     = "orders-events"
)
@Import(KafkaOrderEventListenerIntegrationTest.TestConfig.class)
@SpringBootTest(properties = {
        // 임베디드 Kafka 주소 매핑
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "ordersystem.kafka.topic=orders-events",
        "spring.kafka.consumer.group-id=ordersystem-group",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class KafkaOrderEventListenerIntegrationTest {

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrderUseCase orderUseCase;  // TestConfig에서 mock으로 등록된 빈

    @BeforeEach
    void setUp() {
        // 리스너 컨테이너들이 파티션을 할당받을 때까지 대기
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
        }
    }

    @Test
    void whenPaymentCompletedEvent_thenPrepareShipmentCalled() throws Exception {
        // given: PAYMENT_COMPLETED 이벤트
        OrderEvent event = new OrderEvent(
                100L,
                OrderStatus.PAYMENT_COMPLETED,
                Instant.parse("2025-05-21T14:00:00Z")
        );
        String payload = objectMapper.writeValueAsString(event);

        // when: 메시지 전송 & 전송 보장
        kafkaTemplate.send("orders-events", "100", payload).get();
        kafkaTemplate.flush();

        // then: 최대 5초 대기하며 mock 호출 검증
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        verify(orderUseCase).prepareShipment(100L)
                );
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public OrderUseCase orderUseCase() {
            // 실제 OrderService를 대체하는 mock
            return Mockito.mock(OrderUseCase.class);
        }
    }
}
