package com.example.gigacoffee.domain.point.dto;

import com.example.gigacoffee.domain.point.entity.UserPoint;
import lombok.Getter;

@Getter
public class PointPaymentResponse {

    private final Long pointBalance;
    private final Long paymentAmount;

    public PointPaymentResponse(UserPoint userPoint, Long paymentAmount) {
        this.pointBalance = userPoint.getPointBalance();
        this.paymentAmount = paymentAmount;
    }

    public static PointPaymentResponse of(UserPoint userPoint, Long paymentAmount) {
        return new PointPaymentResponse(userPoint, paymentAmount);
    }
}
