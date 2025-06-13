package com.github.hkjs96.ordersystem.exception;

/**
 * 배송 시작 중 에러 발생 시 던지는 커스텀 예외
 */
public class ShipmentException extends RuntimeException {
    public ShipmentException(String message, Throwable cause) {
        super(message, cause);
    }
    public ShipmentException(String message) {
        super(message);
    }
}
