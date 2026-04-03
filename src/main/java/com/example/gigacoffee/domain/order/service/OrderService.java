package com.example.gigacoffee.domain.order.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.menu.repository.MenuRepository;
import com.example.gigacoffee.domain.order.dto.OrderRequest;
import com.example.gigacoffee.domain.order.dto.OrderResponse;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.repository.OrderRepository;
import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuRequest;
import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;

    @Transactional
    public OrderResponse createOrder(Long userId, OrderRequest request) {
        long totalPrice = 0L;
        List<Menu> menus = new ArrayList<>();

        for (OrderMenuRequest menuRequest : request.getOrderMenus()) {
            Menu menu = menuRepository.findById(menuRequest.getMenuId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));

            if (menu.isDeleted()) {
                throw new BusinessException(ErrorCode.MENU_ALREADY_DELETED);
            }

            menus.add(menu);
            totalPrice += menuRequest.getQuantity() * menu.getPrice();
        }

        Order order = Order.create(userId, totalPrice);

        for (int i = 0; i < menus.size(); i++) {
            OrderMenu orderMenu = OrderMenu.create(
                    order,
                    menus.get(i),
                    request.getOrderMenus().get(i).getQuantity()
            );
            order.getOrderMenus().add(orderMenu);
        }

        orderRepository.save(order);

        return OrderResponse.from(order);
    }
}
