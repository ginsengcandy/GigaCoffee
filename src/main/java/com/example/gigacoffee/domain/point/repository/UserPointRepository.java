package com.example.gigacoffee.domain.point.repository;

import com.example.gigacoffee.domain.point.entity.UserPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPointRepository extends JpaRepository<UserPoint, Long> {
    Optional<UserPoint> findByUserId(Long userId);
}
