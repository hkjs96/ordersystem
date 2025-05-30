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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 취소 후커: DB 커밋 성공 시점에만 호출되어
 * 1) Redis 재고 복귀 (stock: + reserved: 키 모두 처리)
 * 2) Kafka CANCELLED 이벤트 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledListener {

    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final String RESERVED_KEY_PREFIX = "reserved:";

    private final RedisTemplate<String, Integer> redisTemplate;
    private final PublishEventPort eventPort;

    @Value("${ordersystem.inventory.reservation-ttl-seconds:3600}")
    private long reservationTtlSeconds;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("주문 취소 이벤트 수신: orderId={}, productId={}, quantity={}",
                event.orderId(), event.productId(), event.quantity());

        String stockKey = STOCK_KEY_PREFIX + event.productId();
        String reservedKey = RESERVED_KEY_PREFIX + event.productId();

        try {
            // 🔧 1) Redis 재고 복원
            Long restoredStock = redisTemplate.opsForValue().increment(stockKey, event.quantity());
            log.info("Redis 재고 복원 완료: productId={}, quantity={}, newStock={}",
                    event.productId(), event.quantity(), restoredStock);

            // 🔧 2) 예약 수량 정리 (예약된 수량이 있다면 감소)
            Integer currentReserved = redisTemplate.opsForValue().get(reservedKey);
            if (currentReserved != null && currentReserved > 0) {
                Long newReserved = redisTemplate.opsForValue().decrement(reservedKey,
                        Math.min(event.quantity(), currentReserved));
                log.info("예약 수량 정리 완료: productId={}, beforeReserved={}, afterReserved={}",
                        event.productId(), currentReserved, newReserved);
            } else {
                log.debug("예약 수량 없음: productId={}", event.productId());
            }

            // 🔧 3) TTL 관련 처리는 생략 (persist 불필요 - 재고는 영구 보관)

        } catch (Exception e) {
            log.error("Redis 재고 복원 실패: productId={}, quantity={}, error={}",
                    event.productId(), event.quantity(), e.getMessage(), e);
        }

        try {
            // 🔧 4) Kafka CANCELLED 이벤트 발행
            eventPort.publishOrderEvent(new OrderEvent(
                    event.orderId(),
                    OrderStatus.CANCELLED,
                    Instant.now()
            ));
            log.info("주문 취소 Kafka 이벤트 발행 완료: orderId={}", event.orderId());

        } catch (Exception e) {
            log.error("Kafka 이벤트 발행 실패: orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }

        log.info("주문 취소 처리 완료: orderId={}, productId={}, quantity={}",
                event.orderId(), event.productId(), event.quantity());
    }
}