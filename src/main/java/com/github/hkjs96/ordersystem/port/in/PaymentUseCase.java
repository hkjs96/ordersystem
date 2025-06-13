package com.github.hkjs96.ordersystem.port.in;

public interface PaymentUseCase {
    /** 결제 요청 (PG 연동) 상태로 전이 및 이벤트 발행 */
    void initiatePayment(Long orderId);

    /** PG 콜백 핸들러: 승인 완료/실패에 따라 상태 전이 */
    void completePayment(Long orderId, boolean success);
}