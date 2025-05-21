package com.github.hkjs96.ordersystem.domain.event;

public record OrderCancelledEvent(
        Long orderId,
        Long productId,
        int quantity
) {}