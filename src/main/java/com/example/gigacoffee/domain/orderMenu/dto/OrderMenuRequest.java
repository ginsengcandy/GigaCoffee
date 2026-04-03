package com.example.gigacoffee.domain.orderMenu.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderMenuRequest {

    @NotNull(message = "메뉴 ID는 필수입니다.")
    private Long menuId;

    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 0보다 커야 합니다.")
    private int quantity;
}
