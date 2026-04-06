package com.example.gigacoffee.domain.orderMenu.entity;

import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.order.entity.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long unitPrice;

    @Column(nullable = false)
    private int quantity;

    public static OrderMenu create(Order order, Menu menu, int quantity) {
        OrderMenu orderMenu = new OrderMenu();
        orderMenu.order = order;
        orderMenu.menu = menu;
        orderMenu.name = menu.getName();
        orderMenu.unitPrice = menu.getPrice();
        orderMenu.quantity = quantity;
        return orderMenu;
    }
}
