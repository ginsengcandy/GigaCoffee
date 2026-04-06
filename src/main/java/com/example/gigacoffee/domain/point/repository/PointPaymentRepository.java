package com.example.gigacoffee.domain.point.repository;

import com.example.gigacoffee.domain.point.entity.PointPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointPaymentRepository extends JpaRepository<PointPayment, Long> {
    Optional<PointPayment> findByOrderId(Long orderId);
}
