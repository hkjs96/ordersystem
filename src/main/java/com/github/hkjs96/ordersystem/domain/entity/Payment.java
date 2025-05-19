package com.github.hkjs96.ordersystem.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private boolean success;
    private String transactionId;

    private LocalDateTime processedAt;

    @PrePersist
    void onProcess() {
        this.processedAt = LocalDateTime.now();
    }
}
