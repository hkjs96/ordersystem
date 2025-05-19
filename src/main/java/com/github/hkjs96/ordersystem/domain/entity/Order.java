package com.github.hkjs96.ordersystem.domain.entity;

import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private int quantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 상태 전이 검증 메서드 예시 */
    public boolean canTransitionTo(OrderStatus target) {
        // 간단 검증: PAYMENT_FAILED 이후엔 CANCELLED만 허용 등
        if (this.status == OrderStatus.PAYMENT_FAILED && target != OrderStatus.CANCELLED) {
            return false;
        }
        return true;
    }

    public void changeStatus(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition from " + status + " to " + target);
        }
        this.status = target;
    }
}
