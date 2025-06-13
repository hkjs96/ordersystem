package com.github.hkjs96.ordersystem.domain.repository;

import com.github.hkjs96.ordersystem.domain.entity.Delivery;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);

    /**
     * 스케줄러용: 특정 상태이면서 시작일이 cutoff 이전인 배송들 조회
     */
    List<Delivery> findByStatusAndStartedAtBefore(OrderStatus status, LocalDateTime cutoffTime);

    /**
     * 배송 완료 처리용: SHIPPED 상태이면서 배송 시작일이 cutoff 이전인 배송들 조회
     */
    List<Delivery> findByStatusAndShippedAtBefore(OrderStatus status, LocalDateTime cutoffTime);

    /**
     * 상태별 조회
     */
    List<Delivery> findByStatus(OrderStatus status);

    /**
     * 통계용: 특정 상태의 배송 건수 조회
     */
    long countByStatus(OrderStatus status);
}
