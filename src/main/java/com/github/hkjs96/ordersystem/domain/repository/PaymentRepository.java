package com.github.hkjs96.ordersystem.domain.repository;

import com.github.hkjs96.ordersystem.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> { }
