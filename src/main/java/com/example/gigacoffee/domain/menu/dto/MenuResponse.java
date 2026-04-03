package com.example.gigacoffee.domain.menu.dto;

import com.example.gigacoffee.domain.menu.entity.Menu;
import lombok.Getter;

@Getter
public class MenuResponse {

    private final Long id;
    private final String name;
    private final Long price;

    private MenuResponse(Menu menu) {
        this.id = menu.getId();
        this.name = menu.getName();
        this.price = menu.getPrice();
    }

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(menu);
    }
}
