package com.example.gigacoffee.domain.menu.entity;

import com.example.gigacoffee.common.entity.BaseEntity;
import com.example.gigacoffee.domain.menu.dto.MenuRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private boolean deleted = false;

    public static Menu create(String name, Long price) {
        Menu menu = new Menu();
        menu.name = name;
        menu.price = price;
        return menu;
    }

    public static Menu create(MenuRequest dto) {
        Menu menu = new Menu();
        menu.name = dto.getName();
        menu.price = dto.getPrice();
        return menu;
    }

    public void delete() {
        this.deleted = true;
    }
}
