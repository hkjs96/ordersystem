package com.github.hkjs96.ordersystem.adapter.in.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hkjs96.ordersystem.domain.event.InventoryEvent;
import com.github.hkjs96.ordersystem.domain.event.OrderCancelledEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * 주문 취소 이벤트를 카프카로 발행하는 핸들러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventKafkaHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ordersystem.kafka.topics.inventory-events}")
    private String inventoryTopic;

    @Value("${ordersystem.kafka.topics.order-events}")
    private String orderTopic;

    /**
     * 주문 취소 이벤트를 카프카로 발행
     * DB 커밋 후에만 실행되어 데이터 일관성 보장
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("주문 취소 이벤트 처리 시작: orderId={}", event.orderId());

        try {
            // 1. 재고 복원 이벤트 생성
            InventoryEvent inventoryEvent = InventoryEvent.stockReleased(
                    event.orderId(),
                    event.productId(),
                    event.quantity()
            );

            // 2. Kafka로 재고 이벤트 발행
            String inventoryPayload = objectMapper.writeValueAsString(inventoryEvent);
            kafkaTemplate.send(
                    inventoryTopic,
                    String.valueOf(event.productId()), // partition key로 productId 사용
                    inventoryPayload
            ).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("재고 이벤트 발행 실패: {}", ex.getMessage());
                } else {
                    log.info("재고 이벤트 발행 성공: {}", inventoryEvent);
                }
            });

            // 3. 주문 취소 이벤트도 카프카로 발행 (기존 로직 유지)
            OrderEvent orderEvent = new OrderEvent(
                    event.orderId(),
                    OrderStatus.CANCELLED,
                    Instant.now()
            );

            String orderPayload = objectMapper.writeValueAsString(orderEvent);
            kafkaTemplate.send(
                    orderTopic,
                    String.valueOf(event.orderId()),
                    orderPayload
            );

        } catch (Exception e) {
            log.error("카프카 이벤트 발행 실패: orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
            // TODO: 실패 시 재시도 또는 DLQ 처리
        }
    }
}