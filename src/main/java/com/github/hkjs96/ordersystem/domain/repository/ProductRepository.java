package com.github.hkjs96.ordersystem.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.github.hkjs96.ordersystem.domain.entity.Product;

/**
 * JPA 상품 리포지토리
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
