package com.github.hkjs96.ordersystem.adapter.in.event;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import com.github.hkjs96.ordersystem.domain.event.OrderCancelledEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;

/**
 * 주문 취소 후커: DB 커밋 성공 시점에만 호출되어
 * 1) Redis 재고 복귀
 * 2) Kafka CANCELLED 이벤트 발행
 */
@Component
public class OrderCancelledListener {

    private static final String KEY_PREFIX = "inventory:";

    private final RedisTemplate<String, Integer> redisTemplate;
    private final PublishEventPort eventPort;
    private final long reservationTtlSeconds;

    public OrderCancelledListener(RedisTemplate<String, Integer> redisTemplate,
                                  PublishEventPort eventPort,
                                  // reservationTtlSeconds 는 기존 Config 에서 관리
                                  @Value("${ordersystem.inventory.reservation-ttl-seconds:3600}") long reservationTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.eventPort = eventPort;
        this.reservationTtlSeconds = reservationTtlSeconds;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent evt) {
        String key = KEY_PREFIX + evt.productId();
        // Redis 재고 복귀 (TTL은 건드리지 않거나 필요시 persist)
        redisTemplate.opsForValue().increment(key, evt.quantity());
        // 만약 예약 목적 TTL이 남아있어 키가 사라질 우려가 있다면, 아래 코멘트처럼 덧붙이세요.
        // redisTemplate.persist(key);

        // Kafka CANCELLED 이벤트 발행
        eventPort.publishOrderEvent(new OrderEvent(
                evt.orderId(),
                OrderStatus.CANCELLED,
                Instant.now()
        ));
    }
}