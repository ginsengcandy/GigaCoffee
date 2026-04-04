package com.example.gigacoffee.common.payment;

public interface PaymentGateway {
    PaymentResult charge(Long amount);
}
