package com.example.gigacoffee.domain.order.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING(0, "결제 대기"),
    COMPLETED(1, "결제 완료"),
    CANCELLED(2, "결제 취소");

    private final int code;
    private final String description;
}
