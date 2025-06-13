package com.github.hkjs96.ordersystem.port.in;

import com.github.hkjs96.ordersystem.dto.response.DeliveryInfoResponse;

public interface DeliveryUseCase {
    /** 주문이 결제 완료된 후 배송 준비를 시작합니다. */
    void initiateShipment(Long orderId);

    /** 배송 준비→배송 중(Shipped) 상태 전이 */
    void ship(Long orderId);

    /** 배송 중인 주문을 배송 완료 상태로 전이합니다. */
    void completeDelivery(Long orderId);

    /** 배송 정보를 조회합니다. */
    DeliveryInfoResponse getDeliveryInfo(Long orderId);

    /** 배송 추적 정보를 조회합니다. */
    DeliveryInfoResponse getTrackingInfo(Long orderId);
}
