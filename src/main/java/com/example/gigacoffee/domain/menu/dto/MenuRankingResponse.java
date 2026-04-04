package com.example.gigacoffee.domain.menu.dto;

import com.example.gigacoffee.domain.menu.entity.Menu;
import lombok.Getter;

@Getter
public class MenuRankingResponse {

    private final Long menuId;
    private final String name;
    private final Long price;

    private MenuRankingResponse(Menu menu) {
        this.menuId = menu.getId();
        this.name = menu.getName();
        this.price = menu.getPrice();
    }

    public static MenuRankingResponse from(Menu menu) {
        return new MenuRankingResponse(menu);
    }
}
