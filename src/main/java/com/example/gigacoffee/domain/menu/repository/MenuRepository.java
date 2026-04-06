package com.example.gigacoffee.domain.menu.repository;

import com.example.gigacoffee.domain.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findAllByDeletedFalse();
}
