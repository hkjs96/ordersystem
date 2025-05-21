package com.github.hkjs96.ordersystem.adapter.out.cache;

import java.util.concurrent.TimeUnit;

import com.github.hkjs96.ordersystem.exception.InsufficientStockException;
import com.github.hkjs96.ordersystem.exception.ReservationFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.github.hkjs96.ordersystem.port.out.InventoryRepositoryPort;

@Component
public class RedisInventoryRepository implements InventoryRepositoryPort {

    private static final String KEY_PREFIX = "inventory:";

    private final RedisTemplate<String, Integer> redisTemplate;
    private final long reservationTtlSeconds;

    public RedisInventoryRepository(
            RedisTemplate<String, Integer> redisTemplate,
            @Value("${ordersystem.inventory.reservation-ttl-seconds}") long reservationTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.reservationTtlSeconds = reservationTtlSeconds;
    }

    @Override
    public boolean isStockAvailable(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("요청 수량은 최소 1 이상이어야 합니다. requested=" + quantity);
        }
        String key = KEY_PREFIX + productId;
        Integer available = redisTemplate.opsForValue().get(key);
        return available != null && available >= quantity;
    }

    @Override
    public void reserveStock(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("요청 수량은 최소 1 이상이어야 합니다. requested=" + quantity);
        }

        String key = KEY_PREFIX + productId;
        Integer available = redisTemplate.opsForValue().get(key);
        if (available == null || available < quantity) {
            throw new InsufficientStockException(
                    "재고가 부족하여 예약할 수 없습니다. available=" + available + ", requested=" + quantity
            );
        }

        Long remaining = redisTemplate.opsForValue().decrement(key, quantity);
        if (remaining == null || remaining < 0) {
            // 롤백
            redisTemplate.opsForValue().increment(key, quantity);
            throw new ReservationFailedException(
                    "예약 처리 중 예기치 못한 재고 부족 발생, 롤백 완료"
            );
        }

        redisTemplate.expire(key, reservationTtlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void releaseStock(Long productId, int quantity) {
        String key = KEY_PREFIX + productId;
        redisTemplate.opsForValue().increment(key, quantity);
    }

}
