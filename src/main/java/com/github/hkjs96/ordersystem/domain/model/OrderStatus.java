package com.github.hkjs96.ordersystem.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    CREATED,                // 주문 생성 (결제 대기)
    PAYMENT_COMPLETED,      // 결제 성공
    PAYMENT_FAILED,         // 결제 실패
    SHIPMENT_PREPARING,     // 배송 준비
    SHIPPED,                // 배송 중
    DELIVERED,              // 배송 완료
    CANCELLED;               // 주문 취소

    // 허용되는 전이 매핑 정의
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);
    static {
        ALLOWED_TRANSITIONS.put(CREATED, EnumSet.of(PAYMENT_COMPLETED, PAYMENT_FAILED, CANCELLED));
        ALLOWED_TRANSITIONS.put(PAYMENT_COMPLETED, EnumSet.of(SHIPMENT_PREPARING, CANCELLED));
        ALLOWED_TRANSITIONS.put(PAYMENT_FAILED, EnumSet.of(CANCELLED));
        ALLOWED_TRANSITIONS.put(SHIPMENT_PREPARING, EnumSet.of(SHIPPED, CANCELLED));
        ALLOWED_TRANSITIONS.put(SHIPPED, EnumSet.of(DELIVERED));
        ALLOWED_TRANSITIONS.put(DELIVERED, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * 현재 상태에서 target 상태로 전이 가능하면 상태를 리턴하고,
     * 불가능하면 IllegalStateException을 던집니다.
     */
    public OrderStatus next(OrderStatus target) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
        if (allowed.contains(target)) {
            return target;
        }
        throw new IllegalStateException(
                String.format("invalid transition: %s → %s", this, target)
        );
    }
}
