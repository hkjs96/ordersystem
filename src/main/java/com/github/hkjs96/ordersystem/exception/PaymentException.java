package com.github.hkjs96.ordersystem.exception;

/**
 * 결제 처리 중 에러 발생 시 던지는 커스텀 예외
 */
public class PaymentException extends RuntimeException {
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
    public PaymentException(String message) {
        super(message);
    }
}
