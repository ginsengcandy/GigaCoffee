package com.example.gigacoffee.domain.pointCharge.entity;

import com.example.gigacoffee.common.entity.BaseEntity;
import com.example.gigacoffee.domain.pointCharge.enums.PointChargeType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point_charges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointCharge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long chargeAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointChargeType type;

    public static PointCharge create(Long userId, Long amount, PointChargeType type) {
        PointCharge pointCharge = new PointCharge();
        pointCharge.userId = userId;
        pointCharge.chargeAmount = amount;
        pointCharge.type = type;
        return pointCharge;
    }
}
