package com.example.gigacoffee.domain.order.dto;

import com.example.gigacoffee.domain.enums.OrderStatus;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuResponse;
import lombok.Getter;

import java.util.List;

@Getter
public class OrderResponse {

    private final Long orderId;
    private final Long totalPrice;
    private final OrderStatus orderStatus;
    private final List<OrderMenuResponse> orderMenus;

    private OrderResponse(Order order) {
        this.orderId = order.getId();
        this.totalPrice = order.getTotalPrice();
        this.orderStatus = order.getOrderStatus();
        this.orderMenus = order.getOrderMenus().stream()
                .map(OrderMenuResponse::from)
                .toList();
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(order);
    }
}
