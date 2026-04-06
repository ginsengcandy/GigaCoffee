package com.example.gigacoffee.domain.point.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long paymentAmount;

    public static PointPayment create(Long userId, Long orderId, Long paymentAmount) {
        PointPayment pointPayment = new PointPayment();
        pointPayment.userId = userId;
        pointPayment.orderId = orderId;
        pointPayment.paymentAmount = paymentAmount;
        return pointPayment;
    }
}
