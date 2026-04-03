package com.example.gigacoffee.domain.point.repository;

import com.example.gigacoffee.domain.point.entity.PointPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointPaymentRepository extends JpaRepository<PointPayment, Long> {
}
