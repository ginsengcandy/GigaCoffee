package com.example.gigacoffee.domain.menu.dto;

import com.example.gigacoffee.domain.menu.entity.Menu;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class MenuResponse {

    private final Long id;
    private final String name;
    private final Long price;

    @JsonCreator
    private MenuResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name,
            @JsonProperty("price") Long price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(menu.getId(), menu.getName(), menu.getPrice());
    }
}