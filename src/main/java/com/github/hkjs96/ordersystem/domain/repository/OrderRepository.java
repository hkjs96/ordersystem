package com.github.hkjs96.ordersystem.domain.repository;

import com.github.hkjs96.ordersystem.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> { }
