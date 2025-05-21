package com.github.hkjs96.ordersystem.adapter.out.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import com.github.hkjs96.ordersystem.exception.InsufficientStockException;
import com.github.hkjs96.ordersystem.exception.ReservationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisInventoryRepositoryTest {

    private static final long TTL_SECONDS = 3600L;

    @Mock
    private RedisTemplate<String, Integer> redisTemplate;

    @Mock
    private ValueOperations<String, Integer> valueOps;

    private RedisInventoryRepository repository;

    @BeforeEach
    void setUp() {
        // TTL은 생성자에 직접 주입
        repository = new RedisInventoryRepository(redisTemplate, TTL_SECONDS);
    }

    @Test
    @DisplayName("quantity <= 0 이면 isStockAvailable에서 IllegalArgumentException")
    void isStockAvailable_zeroOrNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                repository.isStockAvailable(1L, 0));
        assertThrows(IllegalArgumentException.class, () ->
                repository.isStockAvailable(1L, -5));
    }

    @Test
    @DisplayName("재고가 충분하면 true 반환")
    void isStockAvailable_true() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("inventory:1")).thenReturn(5);

        assertTrue(repository.isStockAvailable(1L, 3));
    }

    @Test
    @DisplayName("재고가 부족하면 false 반환")
    void isStockAvailable_false() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("inventory:1")).thenReturn(2);

        assertFalse(repository.isStockAvailable(1L, 3));
    }

    @Test
    @DisplayName("키가 없으면 false 반환")
    void isStockAvailable_null() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("inventory:1")).thenReturn(null);

        assertFalse(repository.isStockAvailable(1L, 1));
    }

    @Test
    @DisplayName("Redis 접근 중 예외 발생 시 예외 전파")
    void isStockAvailable_redisException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("inventory:1"))
                .thenThrow(new RuntimeException("redis down"));

        assertThrows(RuntimeException.class, () ->
                repository.isStockAvailable(1L, 1));
    }

    @Test
    @DisplayName("reserveStock quantity <= 0 이면 IllegalArgumentException")
    void reserveStock_zeroOrNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                repository.reserveStock(1L, 0));
        assertThrows(IllegalArgumentException.class, () ->
                repository.reserveStock(1L, -3));
    }

    @Test
    @DisplayName("재고 부족 시 InsufficientStockException")
    void reserveStock_insufficientStockThrows() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("inventory:1")).thenReturn(2);

        assertThrows(InsufficientStockException.class, () ->
                repository.reserveStock(1L, 5)
        );
    }

    @Test
    @DisplayName("정상 예약 시 decrement 와 expire 호출")
    void reserveStock_executesDecrementAndExpire() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("inventory:1")).thenReturn(10);
        when(valueOps.decrement("inventory:1", 3)).thenReturn(7L);

        repository.reserveStock(1L, 3);

        verify(valueOps).decrement("inventory:1", 3);
        verify(redisTemplate).expire("inventory:1", TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("decrement 결과가 음수일 때 롤백 후 ReservationFailedException")
    void reserveStock_decrementNegativeResultHandling() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 조회 시점에는 15개가 남아있다고 보고
        when(valueOps.get("inventory:1")).thenReturn(15);
        // 그 사이에 5개가 다른 주문으로 빠져나가서, 12개 예약 시 실제 남은 수량은 -2이라고 시뮬레이션
        when(valueOps.decrement("inventory:1", 12)).thenReturn(-2L);

        ReservationFailedException ex = assertThrows(ReservationFailedException.class, () ->
                repository.reserveStock(1L, 12)
        );
        assertTrue(ex.getMessage().contains("롤백"));

        // 롤백(increment) 호출 확인
        verify(valueOps).increment("inventory:1", 12);
    }

    @Test
    @DisplayName("expire 호출 실패 시 예외 전파")
    void reserveStock_expireException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("inventory:1")).thenReturn(5);
        when(valueOps.decrement("inventory:1", 2)).thenReturn(3L);
        doThrow(new RuntimeException("expire fail"))
                .when(redisTemplate).expire("inventory:1", TTL_SECONDS, TimeUnit.SECONDS);

        assertThrows(RuntimeException.class, () ->
                repository.reserveStock(1L, 2));
    }

}
