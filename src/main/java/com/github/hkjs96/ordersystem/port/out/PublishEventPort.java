package com.github.hkjs96.ordersystem.port.out;

import com.github.hkjs96.ordersystem.domain.model.OrderEvent;

public interface PublishEventPort {

    /**
     * 주문 상태 변경 이벤트를 외부 시스템(Kafka 등)에 게시합니다.
     */
    void publishOrderEvent(OrderEvent event);
}
