package com.example.gigacoffee.common.model.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentConfirmedEvent {

    private Long userId;
    private List<Long> menuIds;
    private Long paymentAmount;
}
