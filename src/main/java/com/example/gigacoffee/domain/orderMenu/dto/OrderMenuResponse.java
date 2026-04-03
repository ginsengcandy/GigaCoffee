package com.example.gigacoffee.domain.orderMenu.dto;

import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({"menuName", "unitPrice", "quantity", "subtotalPrice"})
public class OrderMenuResponse {

    private final String menuName;
    private final Long unitPrice;
    private final int quantity;
    private final Long subtotalPrice;

    private OrderMenuResponse(OrderMenu orderMenu) {
        this.menuName = orderMenu.getName();
        this.unitPrice = orderMenu.getUnitPrice();
        this.quantity = orderMenu.getQuantity();
        this.subtotalPrice = orderMenu.getUnitPrice() * orderMenu.getQuantity();
    }

    public static OrderMenuResponse from(OrderMenu orderMenu) {
        return new OrderMenuResponse(orderMenu);
    }
}
