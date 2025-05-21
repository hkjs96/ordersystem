package com.github.hkjs96.ordersystem.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.dto.request.OrderRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(SpringExtension.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "orders-events")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "ordersystem.kafka.topic=orders-events",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "ordersystem.inventory.reservation-ttl-seconds=3600"
})
@AutoConfigureMockMvc
class OrderCancellationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String,Integer> redisTemplate;

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("주문 취소 통합 테스트 - 취소 플로우가 Redis 재고를 업데이트하고 Kafka 이벤트를 발행하는지 검증")
    void cancelOrder_flow_updatesRedisAndPublishesKafkaEvent() throws Exception {
        // 초기 재고 설정 (50)
        redisTemplate.opsForValue().set("inventory:2", 50);

        // Given: 실제 주문 생성
        OrderRequest orderRequest = new OrderRequest(2L, 3); // productId=2, quantity=3

        // 주문 생성 API 호출
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        // 생성된 주문의 ID 추출
        JsonNode responseJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long orderId = responseJson.path("data").path("orderId").asLong();

        // When: 생성된 주문 취소
        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isOk());

        // Then: Redis 재고 복귀
        ValueOperations<String, Integer> ops = redisTemplate.opsForValue();
        Integer stock = ops.get("inventory:2");
        assertThat(stock).isGreaterThanOrEqualTo(50); // reids 값 47 + 환불된 3

        // And: Kafka CANCELLED 이벤트 발행 확인
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "testGroup", "false", embeddedKafka
        );

        try (var consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()
        ).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "orders-events");

            // 모든 레코드를 가져옴
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer);

            // 해당 주문 ID에 대한 CANCELLED 이벤트를 찾음
            boolean foundCancelEvent = false;
            for (ConsumerRecord<String, String> record : records) {
                OrderEvent evt = objectMapper.readValue(record.value(), OrderEvent.class);
                if (evt.orderId().equals(orderId) && evt.status().equals(OrderStatus.CANCELLED)) {
                    foundCancelEvent = true;
                    break;
                }
            }

            assertThat(foundCancelEvent).isTrue();
        }
    }

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:latest"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379));
        // KafkaContainer 도 추가했다면 동일하게 주입
    }
}

