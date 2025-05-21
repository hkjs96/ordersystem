package com.github.hkjs96.ordersystem.adapter.in.event;

import com.github.hkjs96.ordersystem.domain.event.OrderCancelledEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelledListenerTest {

    private static final long TTL = 3600L;

    @Mock
    private RedisTemplate<String, Integer> redisTemplate;

    @Mock
    private ValueOperations<String, Integer> valueOps;

    @Mock
    private PublishEventPort kafkaPort;

    private OrderCancelledListener listener;

    @BeforeEach
    void setUp() {
        // listener 생성자 인자로 TTL을 주입하려면 수동 생성이 필요할 수도 있습니다.
        listener = new OrderCancelledListener(redisTemplate, kafkaPort, TTL);
        // opsForValue() 호출 시 언제나 valueOps를 반환
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("OrderCancelledEvent 처리 시 Redis 재고 복귀 및 Kafka 이벤트 발행")
    void onOrderCancelled_releasesAndPublishes() {
        // given
        OrderCancelledEvent evt = new OrderCancelledEvent(1L, 2L, 3);

        // when
        listener.onOrderCancelled(evt);

        // then: Redis increment 호출 검증
        verify(valueOps).increment("inventory:2", 3);
        // TTL은 복귀시 건드리지 않는 전략을 채택했으므로 expire 미검증

        // then: Kafka CANCELLED 이벤트 발행 검증
        verify(kafkaPort).publishOrderEvent(argThat(orderEvent ->
                orderEvent.orderId() == 1L &&
                orderEvent.status() == OrderStatus.CANCELLED
        ));
    }
}