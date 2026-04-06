package com.example.gigacoffee.common.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class PaymentResult {

    private final boolean success;
    private final String transactionId;

    public static PaymentResult success() {
        return new PaymentResult(true, UUID.randomUUID().toString());
    }

    public static PaymentResult fail() {
        return new PaymentResult(false, null);
    }
}
