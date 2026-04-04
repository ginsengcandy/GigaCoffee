package com.example.gigacoffee.domain.menu.dto;

import com.example.gigacoffee.domain.menu.entity.Menu;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({"rank", "menuId", "name", "price"})
public class MenuRankingResponse {

    private final int rank;
    private final Long menuId;
    private final String name;
    private final Long price;

    private MenuRankingResponse(int rank, Menu menu) {
        this.rank = rank;
        this.menuId = menu.getId();
        this.name = menu.getName();
        this.price = menu.getPrice();
    }

    public static MenuRankingResponse of(int rank, Menu menu) {
        return new MenuRankingResponse(rank, menu);
    }
}
