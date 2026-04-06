package com.example.gigacoffee.domain.order.dto;

import com.example.gigacoffee.domain.order.enums.OrderStatus;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuResponse;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class OrderResponse {

    private final Long id;
    private final Long totalPrice;
    private final OrderStatus orderStatus;
    private final List<OrderMenuResponse> orderMenus;

    @JsonCreator
    private OrderResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("totalPrice") Long totalPrice,
            @JsonProperty("orderStatus") OrderStatus orderStatus,
            @JsonProperty("orderMenus") List<OrderMenuResponse> orderMenus
    ) {
       this.id = id;
       this.totalPrice = totalPrice;
       this.orderStatus = orderStatus;
       this.orderMenus = orderMenus;
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTotalPrice(),
                order.getOrderStatus(),
                order.getOrderMenus().stream()
                        .map(OrderMenuResponse::from)
                        .toList()
        );
    }
}
