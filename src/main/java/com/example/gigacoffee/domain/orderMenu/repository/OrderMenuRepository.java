package com.example.gigacoffee.domain.orderMenu.repository;

import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderMenuRepository extends JpaRepository<OrderMenu, Long> {
}
