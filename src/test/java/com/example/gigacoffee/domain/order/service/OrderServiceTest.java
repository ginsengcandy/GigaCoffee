package com.example.gigacoffee.domain.order.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.menu.repository.MenuRepository;
import com.example.gigacoffee.domain.order.dto.OrderRequest;
import com.example.gigacoffee.domain.order.dto.OrderResponse;
import com.example.gigacoffee.domain.order.enums.OrderStatus;
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

    // ========================
    // 정상 케이스
    // ========================

    @Test
    @DisplayName("단일 메뉴 정상 주문 생성")
    void createOrder_singleMenu_success() {
        // given
        Menu menu = Menu.create("아메리카노", 4500L);
        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 2)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getOrderMenus()).hasSize(1);
        verify(orderRepository).save(any());
    }

    @Test
    @DisplayName("복수 메뉴 정상 주문 생성")
    void createOrder_multipleMenus_success() {
        // given
        Menu americano = Menu.create("아메리카노", 4500L);
        Menu latte = Menu.create("라떼", 5000L);
        OrderRequest request = orderRequest(List.of(
                orderMenuRequest(1L, 1),
                orderMenuRequest(2L, 1)
        ));

        given(menuRepository.findById(1L)).willReturn(Optional.of(americano));
        given(menuRepository.findById(2L)).willReturn(Optional.of(latte));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getOrderMenus()).hasSize(2);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(any());
    }

    @Test
    @DisplayName("주문 생성 시 총 가격이 정확히 계산된다 (4500 × 2 + 5000 × 1 = 14000)")
    void createOrder_totalPriceCalculatedCorrectly() {
        // given
        Menu americano = Menu.create("아메리카노", 4500L);
        Menu latte = Menu.create("라떼", 5000L);
        OrderRequest request = orderRequest(List.of(
                orderMenuRequest(1L, 2),
                orderMenuRequest(2L, 1)
        ));

        given(menuRepository.findById(1L)).willReturn(Optional.of(americano));
        given(menuRepository.findById(2L)).willReturn(Optional.of(latte));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getTotalPrice()).isEqualTo(14000L);
    }

    @Test
    @DisplayName("주문 생성 시 스냅샷 컬럼(name, unitPrice)이 메뉴 현재값으로 저장된다")
    void createOrder_snapshotColumns_savedWithCurrentMenuValues() {
        // given
        Menu menu = Menu.create("아메리카노", 4500L);
        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 1)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getOrderMenus().get(0).getMenuName()).isEqualTo("아메리카노");
        assertThat(response.getOrderMenus().get(0).getUnitPrice()).isEqualTo(4500L);
    }

    // ========================
    // 메뉴 관련
    // ========================

    @Test
    @DisplayName("존재하지 않는 메뉴 주문 시 MENU_NOT_FOUND 예외를 던진다")
    void createOrder_menuNotFound_throwsException() {
        // given
        OrderRequest request = orderRequest(List.of(orderMenuRequest(999L, 1)));

        given(menuRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 메뉴 주문 시 MENU_ALREADY_DELETED 예외를 던진다")
    void createOrder_deletedMenu_throwsException() {
        // given
        Menu deletedMenu = Menu.create("아메리카노", 4500L);
        deletedMenu.delete();
        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 1)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(deletedMenu));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_ALREADY_DELETED));
    }

    @Test
    @DisplayName("존재하는 메뉴와 삭제된 메뉴 혼합 주문 시 MENU_ALREADY_DELETED 예외를 던진다")
    void createOrder_mixedMenus_throwsMenuAlreadyDeletedException() {
        // given
        Menu normalMenu = Menu.create("아메리카노", 4500L);
        Menu deletedMenu = Menu.create("라떼", 5000L);
        deletedMenu.delete();
        OrderRequest request = orderRequest(List.of(
                orderMenuRequest(1L, 1),
                orderMenuRequest(2L, 1)
        ));

        given(menuRepository.findById(1L)).willReturn(Optional.of(normalMenu));
        given(menuRepository.findById(2L)).willReturn(Optional.of(deletedMenu));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_ALREADY_DELETED));
    }

    // ========================
    // 수량 관련
    // ========================

    @Test
    @DisplayName("수량이 1일 때 정상 주문된다")
    void createOrder_quantityOne_success() {
        // given
        Menu menu = Menu.create("아메리카노", 4500L);
        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 1)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getTotalPrice()).isEqualTo(4500L);
        assertThat(response.getOrderMenus().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 메뉴를 여러 번 담으면 각각 별도 OrderMenu로 저장된다")
    void createOrder_duplicateMenu_savedAsSeparateOrderMenus() {
        // given
        Menu menu = Menu.create("아메리카노", 4500L);
        OrderRequest request = orderRequest(List.of(
                orderMenuRequest(1L, 1),
                orderMenuRequest(1L, 3)
        ));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getOrderMenus()).hasSize(2);
        assertThat(response.getOrderMenus().get(0).getQuantity()).isEqualTo(1);
        assertThat(response.getOrderMenus().get(1).getQuantity()).isEqualTo(3);
        assertThat(response.getTotalPrice()).isEqualTo(18000L); // 4500 * (1 + 3)
    }

    // ========================
    // 헬퍼 메서드
    // ========================

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
