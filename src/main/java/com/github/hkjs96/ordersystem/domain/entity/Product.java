package com.github.hkjs96.ordersystem.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상품 엔티티
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // 🔧 재고 관리 필드 추가

    /** 총 재고 설정값 (관리자가 설정, 변경 빈도 낮음) */
    @Column(nullable = false)
    @Builder.Default
    private Integer totalStock = 0;

    /** 재고 관리 활성화 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean stockManagementEnabled = true;

    // 🔧 비즈니스 로직 메서드들

    /**
     * 재고 관리 대상 여부 확인
     */
    public boolean isStockManaged() {
        return stockManagementEnabled;
    }

    /**
     * 총 재고 설정 (관리자 기능)
     */
    public void setTotalStock(Integer totalStock) {
        if (totalStock < 0) {
            throw new IllegalArgumentException("총 재고는 0 이상이어야 합니다: " + totalStock);
        }
        this.totalStock = totalStock;
    }

    /**
     * 재고 관리 활성화/비활성화
     */
    public void setStockManagementEnabled(Boolean enabled) {
        this.stockManagementEnabled = enabled;
    }
}