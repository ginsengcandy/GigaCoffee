package com.example.gigacoffee.common.kafka.model.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentConfirmedEvent {

    private Long userId;
    private List<MenuQuantity> menuQuantities;
    private Long paymentAmount;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MenuQuantity {
        private Long menuId;
        private int quantity;
    }
}
