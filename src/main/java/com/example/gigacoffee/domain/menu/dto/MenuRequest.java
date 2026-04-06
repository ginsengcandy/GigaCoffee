package com.example.gigacoffee.domain.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MenuRequest {

    @NotBlank(message = "메뉴 이름은 필수입니다.")
    private String name;

    @NotNull(message = "가격은 필수입니다.")
    @Positive(message = "가격은 0보다 커야 합니다.")
    private Long price;
}
