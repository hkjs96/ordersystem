package com.github.hkjs96.ordersystem.port.out;

public interface InventoryRepositoryPort {

    /**
     * 재고가 충분한지 확인합니다.
     */
    boolean isStockAvailable(Long productId, int quantity);

    /**
     * 재고를 예약(감소) 처리합니다.
     * TTL 기반 롤백 로직은 구현체에서 처리합니다.
     */
    void reserveStock(Long productId, int quantity);

    /**
     * 주문 취소시 재고를 원복 처리합니다.
     */
    void releaseStock(Long productId, int quantity);  // 신규
}
