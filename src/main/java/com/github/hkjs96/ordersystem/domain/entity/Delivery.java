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

    /** 배송 시작일시 */
    private LocalDateTime startedAt;

    /** 배송 중 상태로 변경된 시간 */
    private LocalDateTime shippedAt;

    /** 배송 완료일시 */
    private LocalDateTime completedAt;

    /** 송장번호 (간소화: 자동 생성) */
    private String trackingNumber;

    /** 택배사 */
    private String courierCompany;

    @PrePersist
    void onStart() {
        this.startedAt = LocalDateTime.now();
        this.status = OrderStatus.SHIPMENT_PREPARING;
        this.courierCompany = "CJ대한통운";
    }

    /** 배송 시작 처리 */
    public void markShipped() {
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = LocalDateTime.now();

        // 🔧 간소화: 배송 시작 시점에 자동으로 송장번호 생성
        if (this.trackingNumber == null) {
            this.trackingNumber = "TRACK-" + this.orderId + "-" + System.currentTimeMillis() % 100000;
        }
    }

    /** 배송 완료 처리 */
    public void markDelivered() {
        this.status = OrderStatus.DELIVERED;
        this.completedAt = LocalDateTime.now();
    }
}
