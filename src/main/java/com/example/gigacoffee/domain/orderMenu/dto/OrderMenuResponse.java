package com.example.gigacoffee.domain.orderMenu.dto;

import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({"menuName", "unitPrice", "quantity", "subtotalPrice"})
public class OrderMenuResponse {

    private final String menuName;
    private final Long unitPrice;
    private final int quantity;
    private final Long subtotalPrice;

    @JsonCreator
    private OrderMenuResponse(
            @JsonProperty("menuName") String menuName,
            @JsonProperty("unitPrice") Long unitPrice,
            @JsonProperty("quantity") int quantity
    ) {
        this.menuName = menuName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotalPrice = unitPrice * quantity;
    }

    public static OrderMenuResponse from(OrderMenu orderMenu) {
        return new OrderMenuResponse(
                orderMenu.getName(),
                orderMenu.getUnitPrice(),
                orderMenu.getQuantity()
        );
    }
}
