package com.github.hkjs96.ordersystem.domain.model;

import java.time.Instant;

/**
 * 주문 상태 변경 이벤트
 */
public record OrderEvent(
        Long orderId,
        OrderStatus status,
        Instant timestamp
) {
    /**
     * 생성 시점의 타임스탬프를 자동으로 설정하는 헬퍼 생성자
     */
    public OrderEvent(Long orderId, OrderStatus status) {
        this(orderId, status, Instant.now());
    }
}
