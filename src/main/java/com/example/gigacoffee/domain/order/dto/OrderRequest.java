package com.example.gigacoffee.domain.order.dto;

import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuRequest;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderRequest {

    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다.")
    private List<OrderMenuRequest>  orderMenus;
}
