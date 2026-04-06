package com.example.gigacoffee.domain.point.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointChargeType {

    CHARGE(0, "충전"),
    REFUND(1, "환불"),
    PROMOTION(2, "프로모션");

    private final int code;
    private final String description;
}
