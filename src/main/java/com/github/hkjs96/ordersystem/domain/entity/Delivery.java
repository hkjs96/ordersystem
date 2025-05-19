package com.github.hkjs96.ordersystem.domain.entity;

import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Delivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @PrePersist
    void onStart() {
        this.startedAt = LocalDateTime.now();
        this.status = OrderStatus.SHIPMENT_PREPARING;
    }

    /** 배송 완료 처리 */
    public void markShipped() {
        this.status = OrderStatus.SHIPPED;
    }

    public void markDelivered() {
        this.status = OrderStatus.DELIVERED;
        this.completedAt = LocalDateTime.now();
    }
}
