package com.github.hkjs96.ordersystem.adapter.out.cache;

import org.springframework.stereotype.Component;
import com.github.hkjs96.ordersystem.port.out.InventoryRepositoryPort;

/**
 * 모놀리식 단계용 더미 재고 어댑터.
 * 항상 재고가 충분하다고 응답하고, 예약은 no-op 처리합니다.
 */
@Component
public class DummyInventoryRepository implements InventoryRepositoryPort {

    @Override
    public boolean isStockAvailable(Long productId, int quantity) {
        // TODO: Redis 기반 실제 구현으로 교체 예정
        return true;
    }

    @Override
    public void reserveStock(Long productId, int quantity) {
        // TODO: Redis SET + EXPIRE 로직으로 교체 예정
    }
}
