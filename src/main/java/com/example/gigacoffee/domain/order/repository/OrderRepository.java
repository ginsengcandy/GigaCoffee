package com.example.gigacoffee.domain.order.repository;

import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(Long userId, OrderStatus orderStatus);
}
