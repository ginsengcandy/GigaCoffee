package com.example.gigacoffee.domain.order.repository;

import com.example.gigacoffee.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
