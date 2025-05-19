package com.github.hkjs96.ordersystem.port.in;

import com.github.hkjs96.ordersystem.dto.request.CreateOrderRequest;
import com.github.hkjs96.ordersystem.dto.response.OrderResponse;

public interface OrderUseCase {

    /**
     * 새로운 주문을 생성하고, 생성된 주문의 요약 정보를 반환합니다.
     */
    OrderResponse createOrder(CreateOrderRequest request);

    /**
     * 주문을 취소 상태로 전환합니다.
     */
    void cancelOrder(Long orderId);
}
