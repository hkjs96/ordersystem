package com.github.hkjs96.ordersystem.exception;

/**
 * 배송 처리 중 에러 발생 시 던지는 커스텀 예외
 */
public class DeliveryException extends RuntimeException {

    public DeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public DeliveryException(String message) {
        super(message);
    }
}