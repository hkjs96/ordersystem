package com.github.hkjs96.ordersystem.adapter.out.cache;

import java.util.concurrent.TimeUnit;

import com.github.hkjs96.ordersystem.domain.entity.Product;
import com.github.hkjs96.ordersystem.domain.repository.ProductRepository;
import com.github.hkjs96.ordersystem.exception.InsufficientStockException;
import com.github.hkjs96.ordersystem.exception.ReservationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.github.hkjs96.ordersystem.port.out.InventoryRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis + DB 하이브리드 재고 관리 구현체
 *
 * 📋 동작 원리:
 * - 주문 생성: Redis 빠른 체크 + 예약
 * - 결제 완료: DB 실제 차감 + Redis 정리
 * - 주문 취소: Redis 복원
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryRepository implements InventoryRepositoryPort {

    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final String RESERVED_KEY_PREFIX = "reserved:";
    private final RedisTemplate<String, Integer> redisTemplate;
    private final ProductRepository productRepository;

    @Override
    public boolean isStockAvailable(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("요청 수량은 최소 1 이상이어야 합니다. requested=" + quantity);
        }

        String stockKey = STOCK_KEY_PREFIX + productId;
        Integer available = redisTemplate.opsForValue().get(stockKey);

        // Redis에 재고 정보가 없으면 DB에서 초기화
        if (available == null) {
            available = initializeFromDatabase(productId);
        }

        boolean result = available >= quantity;
        log.debug("재고 확인: productId={}, requested={}, available={}, result={}",
                productId, quantity, available, result);

        return result;
    }

    @Override
    public void reserveStock(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("요청 수량은 최소 1 이상이어야 합니다. requested=" + quantity);
        }

        String stockKey = STOCK_KEY_PREFIX + productId;
        String reservedKey = RESERVED_KEY_PREFIX + productId;

        // Redis에서 재고 확인 및 차감
        Integer available = redisTemplate.opsForValue().get(stockKey);
        if (available == null) {
            available = initializeFromDatabase(productId);
        }

        if (available < quantity) {
            throw new InsufficientStockException(
                    "재고가 부족하여 예약할 수 없습니다. available=" + available + ", requested=" + quantity
            );
        }

        // Redis 원자적 연산으로 재고 차감
        Long remaining = redisTemplate.opsForValue().decrement(stockKey, quantity);
        if (remaining == null || remaining < 0) {
            // 롤백
            redisTemplate.opsForValue().increment(stockKey, quantity);
            throw new ReservationFailedException("예약 처리 중 예기치 못한 재고 부족 발생, 롤백 완료");
        }

        // 예약 수량 기록 (1시간 TTL)
        redisTemplate.opsForValue().increment(reservedKey, quantity);
        redisTemplate.expire(reservedKey, 1, TimeUnit.HOURS);

        log.info("재고 예약 완료: productId={}, quantity={}, remaining={}", productId, quantity, remaining);
    }

    @Override
    public void releaseStock(Long productId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String reservedKey = RESERVED_KEY_PREFIX + productId;

        // 재고 복원
        redisTemplate.opsForValue().increment(stockKey, quantity);

        // 예약 수량 감소
        Integer currentReserved = redisTemplate.opsForValue().get(reservedKey);
        if (currentReserved != null && currentReserved > 0) {
            redisTemplate.opsForValue().decrement(reservedKey, Math.min(quantity, currentReserved));
        }

        log.info("재고 복원 완료: productId={}, quantity={}", productId, quantity);
    }

    /**
     * 🔧 새로 추가: 결제 완료 시 DB에 실제 재고 차감
     */
    @Transactional
    public void confirmSale(Long productId, int quantity, Long orderId) {
        log.info("재고 판매 확정: productId={}, quantity={}, orderId={}", productId, quantity, orderId);

        // 1. DB에서 실제 재고 차감
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 미발견: " + productId));

        if (!product.isStockManaged()) {
            log.debug("재고 관리 비활성화 상품: productId={}", productId);
            return;
        }

        // 2. DB 재고 검증 및 차감
        if (product.getTotalStock() < quantity) {
            log.error("DB 재고 부족 감지: productId={}, dbStock={}, requested={}",
                    productId, product.getTotalStock(), quantity);

            // Redis와 DB 동기화
            syncWithDatabase(productId);
            throw new InsufficientStockException("DB 재고 부족: 동기화 완료");
        }

        // 3. DB 재고 차감 (핵심 포인트!)
        product.setTotalStock(product.getTotalStock() - quantity);
        productRepository.save(product);

        // 4. Redis 예약 수량 정리
        String reservedKey = RESERVED_KEY_PREFIX + productId;
        Integer currentReserved = redisTemplate.opsForValue().get(reservedKey);
        if (currentReserved != null && currentReserved >= quantity) {
            redisTemplate.opsForValue().decrement(reservedKey, quantity);
        }

        log.info("재고 차감 완료: productId={}, quantity={}, newDbStock={}",
                productId, quantity, product.getTotalStock());
    }

    /**
     * DB에서 Redis로 재고 정보 초기화
     */
    private Integer initializeFromDatabase(Long productId) {
        log.info("DB에서 재고 초기화: productId={}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 미발견: " + productId));

        int stockValue = product.isStockManaged() ? product.getTotalStock() : Integer.MAX_VALUE;

        String stockKey = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(stockKey, stockValue);
        redisTemplate.expire(stockKey, 24, TimeUnit.HOURS);

        log.info("재고 초기화 완료: productId={}, stock={}", productId, stockValue);
        return stockValue;
    }

    /**
     * Redis와 DB 동기화
     */
    @Transactional(readOnly = true)
    public void syncWithDatabase(Long productId) {
        log.warn("Redis-DB 동기화 시작: productId={}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 미발견: " + productId));

        int correctStock = product.isStockManaged() ? product.getTotalStock() : Integer.MAX_VALUE;

        redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + productId, correctStock);
        redisTemplate.delete(RESERVED_KEY_PREFIX + productId); // 예약 초기화

        log.warn("동기화 완료: productId={}, correctStock={}", productId, correctStock);
    }

    /**
     * 재고 상태 조회 (모니터링용)
     */
    @Transactional(readOnly = true)
    public StockStatus getStockStatus(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 미발견: " + productId));

        Integer redisStock = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + productId);
        Integer reservedStock = redisTemplate.opsForValue().get(RESERVED_KEY_PREFIX + productId);

        return new StockStatus(
                productId,
                product.getTotalStock(),              // DB 실제 재고
                redisStock != null ? redisStock : 0, // Redis 현재 재고
                reservedStock != null ? reservedStock : 0 // 예약된 재고
        );
    }

    public record StockStatus(
            Long productId,
            Integer databaseStock,
            Integer redisStock,
            Integer reservedStock
    ) {}
}
