package com.example.gigacoffee.domain.order.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.enums.OrderStatus;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.menu.repository.MenuRepository;
import com.example.gigacoffee.domain.order.dto.OrderRequest;
import com.example.gigacoffee.domain.order.dto.OrderResponse;
import com.example.gigacoffee.domain.order.repository.OrderRepository;
import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MenuRepository menuRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("정상 주문 생성 - totalPrice와 PENDING 상태를 반환한다")
    void createOrder_success() {
        // given
        Long userId = 1L;
        Menu menu = Menu.create("아메리카노", 4500L);

        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 2)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(userId, request);

        // then
        assertThat(response.getTotalPrice()).isEqualTo(9000L);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getOrderMenus()).hasSize(1);
        assertThat(response.getOrderMenus().get(0).getMenuName()).isEqualTo("아메리카노");
        assertThat(response.getOrderMenus().get(0).getSubtotalPrice()).isEqualTo(9000L);

        verify(orderRepository).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 메뉴 주문 시 MENU_NOT_FOUND 예외를 던진다")
    void createOrder_menuNotFound_throwsException() {
        // given
        Long userId = 1L;
        Long nonExistentMenuId = 999L;

        OrderRequest request = orderRequest(List.of(orderMenuRequest(nonExistentMenuId, 1)));

        given(menuRepository.findById(nonExistentMenuId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 메뉴 주문 시 MENU_ALREADY_DELETED 예외를 던진다")
    void createOrder_deletedMenu_throwsException() {
        // given
        Long userId = 1L;
        Menu deletedMenu = Menu.create("아메리카노", 4500L);
        deletedMenu.delete();

        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 1)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(deletedMenu));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_ALREADY_DELETED));
    }

    private OrderMenuRequest orderMenuRequest(Long menuId, int quantity) {
        OrderMenuRequest req = new OrderMenuRequest();
        ReflectionTestUtils.setField(req, "menuId", menuId);
        ReflectionTestUtils.setField(req, "quantity", quantity);
        return req;
    }

    private OrderRequest orderRequest(List<OrderMenuRequest> items) {
        OrderRequest req = new OrderRequest();
        ReflectionTestUtils.setField(req, "orderMenus", items);
        return req;
    }
}
