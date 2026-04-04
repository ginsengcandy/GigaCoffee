package com.example.gigacoffee.common.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@Slf4j
public class MockPaymentGateway implements PaymentGateway{

    @Override
    public PaymentResult charge(Long amount) {
        log.info("[MockPayment] 결제 요청 - amount: {}", amount);
        return PaymentResult.success();
    }
}
