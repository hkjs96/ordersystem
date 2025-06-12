package com.github.hkjs96.ordersystem.domain.event;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카프카로 전송될 재고 이벤트 모델
 * 기존 OrderCancelledEvent를 대체
 */
public record InventoryEvent(
        @JsonProperty("eventType") String eventType,  // "STOCK_RESERVED", "STOCK_RELEASED", "STOCK_CONFIRMED"
        @JsonProperty("orderId") Long orderId,
        @JsonProperty("productId") Long productId,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("timestamp") Instant timestamp
) {
    // 이벤트 타입 상수
    public static final String STOCK_RESERVED = "STOCK_RESERVED";
    public static final String STOCK_RELEASED = "STOCK_RELEASED";
    public static final String STOCK_CONFIRMED = "STOCK_CONFIRMED";

    // 팩토리 메서드들
    public static InventoryEvent stockReserved(Long orderId, Long productId, Integer quantity) {
        return new InventoryEvent(STOCK_RESERVED, orderId, productId, quantity, Instant.now());
    }

    public static InventoryEvent stockReleased(Long orderId, Long productId, Integer quantity) {
        return new InventoryEvent(STOCK_RELEASED, orderId, productId, quantity, Instant.now());
    }

    public static InventoryEvent stockConfirmed(Long orderId, Long productId, Integer quantity) {
        return new InventoryEvent(STOCK_CONFIRMED, orderId, productId, quantity, Instant.now());
    }
}