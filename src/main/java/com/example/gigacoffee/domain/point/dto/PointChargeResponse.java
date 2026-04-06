package com.example.gigacoffee.domain.point.dto;

import com.example.gigacoffee.domain.point.entity.UserPoint;
import lombok.Getter;

@Getter
public class PointChargeResponse {

    private final Long pointBalance;
    private final Long chargeAmount;

    public PointChargeResponse(UserPoint userPoint, Long chargeAmount) {
        this.pointBalance = userPoint.getPointBalance();
        this.chargeAmount = chargeAmount;
    }

    public static PointChargeResponse of(UserPoint userPoint, Long chargeAmount) {
        return new PointChargeResponse(userPoint, chargeAmount);
    }
}
