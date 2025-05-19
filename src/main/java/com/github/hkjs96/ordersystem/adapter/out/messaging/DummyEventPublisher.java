package com.github.hkjs96.ordersystem.adapter.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;

/**
 * 모놀리식 단계용 더미 이벤트 퍼블리셔.
 * 실제 Kafka 퍼블리셔로 교체 예정입니다.
 */
@Component
public class DummyEventPublisher implements PublishEventPort {

    private static final Logger log = LoggerFactory.getLogger(DummyEventPublisher.class);

    @Override
    public void publishOrderEvent(OrderEvent event) {
        // TODO: KafkaEventPublisher로 교체
        log.info("Dummy publish event: orderId={}, status={}, ts={}",
                event.orderId(), event.status(), event.timestamp());
    }
}
