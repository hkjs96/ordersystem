package com.github.hkjs96.ordersystem.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.hkjs96.ordersystem.domain.model.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderStatusTransitionTest {

    @Test
    @DisplayName("CREATED → PAYMENT_COMPLETED 로 전이 가능")
    void transitionToPaymentCompleted() {
        assertDoesNotThrow(() -> CREATED.next(PAYMENT_COMPLETED));
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED 이후 SHIPMENT_PREPARING 로 전이 가능")
    void transitionToPreparing() {
        assertDoesNotThrow(() -> PAYMENT_COMPLETED.next(SHIPMENT_PREPARING));
    }

    @Test
    @DisplayName("DELIVERED 이후에는 더 이상 전이 불가 (예외 발생)")
    void noTransitionAfterDelivered() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> DELIVERED.next(CANCELLED)
        );
        assertTrue(ex.getMessage().contains("invalid transition"));
    }

}
