package com.github.hkjs96.ordersystem.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hkjs96.ordersystem.domain.event.InventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 재고 관련 카프카 이벤트를 처리하는 Consumer
 * 주문 취소, 결제 실패 등으로 인한 재고 복원 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaInventoryEventListener {

    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final String RESERVED_KEY_PREFIX = "reserved:";

    private final RedisTemplate<String, Integer> redisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${ordersystem.kafka.topics.inventory-events}",
            groupId = "${spring.kafka.consumer.group-id}-inventory"
    )
    public void handleInventoryEvent(String message) {
        log.debug("재고 이벤트 수신: {}", message);

        try {
            // JSON 파싱
            InventoryEvent event = objectMapper.readValue(message, InventoryEvent.class);

            // 이벤트 타입별 처리
            switch (event.eventType()) {
                case InventoryEvent.STOCK_RELEASED -> handleStockReleased(event);
                case InventoryEvent.STOCK_RESERVED -> handleStockReserved(event);
                case InventoryEvent.STOCK_CONFIRMED -> handleStockConfirmed(event);
                default -> log.warn("알 수 없는 이벤트 타입: {}", event.eventType());
            }

        } catch (Exception e) {
            log.error("재고 이벤트 처리 실패: message={}, error={}",
                    message, e.getMessage(), e);
            // TODO: 에러 처리 - 재시도 또는 DLQ
        }
    }

    /**
     * 재고 복원 처리 (주문 취소, 결제 실패 시)
     */
    private void handleStockReleased(InventoryEvent event) {
        log.info("재고 복원 시작: orderId={}, productId={}, quantity={}",
                event.orderId(), event.productId(), event.quantity());

        String stockKey = STOCK_KEY_PREFIX + event.productId();
        String reservedKey = RESERVED_KEY_PREFIX + event.productId();

        try {
            // 1. Redis 재고 복원
            Long restoredStock = redisTemplate.opsForValue()
                    .increment(stockKey, event.quantity());

            log.info("Redis 재고 복원 완료: productId={}, quantity={}, newStock={}",
                    event.productId(), event.quantity(), restoredStock);

            // 2. 예약 수량 감소
            Integer currentReserved = redisTemplate.opsForValue().get(reservedKey);
            if (currentReserved != null && currentReserved > 0) {
                Long newReserved = redisTemplate.opsForValue()
                        .decrement(reservedKey, Math.min(event.quantity(), currentReserved));

                log.info("예약 수량 조정: productId={}, before={}, after={}",
                        event.productId(), currentReserved, newReserved);
            }

        } catch (Exception e) {
            log.error("Redis 재고 복원 실패: productId={}, error={}",
                    event.productId(), e.getMessage(), e);
            // Redis 장애 시 DB 직접 업데이트 등의 Fallback 로직 필요
        }
    }

    /**
     * 재고 예약 처리 (분산 환경에서 다른 서비스가 예약 요청 시)
     */
    private void handleStockReserved(InventoryEvent event) {
        log.info("재고 예약 이벤트 처리: orderId={}, productId={}, quantity={}",
                event.orderId(), event.productId(), event.quantity());

        // 현재는 OrderService에서 직접 처리하므로 로깅만
        // 추후 MSA 전환 시 구현
    }

    /**
     * 재고 확정 처리 (결제 완료 후)
     */
    private void handleStockConfirmed(InventoryEvent event) {
        log.info("재고 확정 이벤트 처리: orderId={}, productId={}, quantity={}",
                event.orderId(), event.productId(), event.quantity());

        // 예약된 재고를 확정 처리
        String reservedKey = RESERVED_KEY_PREFIX + event.productId();

        try {
            Integer currentReserved = redisTemplate.opsForValue().get(reservedKey);
            if (currentReserved != null && currentReserved >= event.quantity()) {
                redisTemplate.opsForValue().decrement(reservedKey, event.quantity());
                log.info("예약 재고 확정 완료: productId={}, quantity={}",
                        event.productId(), event.quantity());
            }
        } catch (Exception e) {
            log.error("예약 재고 확정 실패: productId={}, error={}",
                    event.productId(), e.getMessage(), e);
        }
    }
}