package com.example.gigacoffee.domain.point.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PointPaymentRequest {

    @NotNull(message = "주문 ID는 필수입니다.")
    private Long orderId;
}
