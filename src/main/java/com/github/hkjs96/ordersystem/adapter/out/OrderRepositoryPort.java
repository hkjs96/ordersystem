package com.github.hkjs96.ordersystem.adapter.out;

import com.github.hkjs96.ordersystem.domain.entity.Order;
import java.util.Optional;

public interface OrderRepositoryPort {
    /**
     * 주문을 저장합니다.
     */
    Order save(Order order);

    /**
     * 주문 ID로 주문을 조회합니다.
     */
    Optional<Order> findById(Long orderId);

    /**
     * 주문을 삭제합니다.
     */
    void deleteById(Long orderId);

    /**
     * 주문 존재 여부를 확인합니다.
     */
    boolean existsById(Long orderId);
}