package com.example.gigacoffee.domain.order.controller;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.order.dto.OrderRequest;
import com.example.gigacoffee.domain.order.dto.OrderResponse;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.service.OrderService;
import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuRequest;
import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("정상 주문 생성 - 200 OK와 주문 정보를 반환한다")
    void createOrder_success() throws Exception {
        // given
        Order order = Order.create(1L, 9000L);
        Menu menu = Menu.create("아메리카노", 4500L);
        order.getOrderMenus().add(OrderMenu.create(order, menu, 2));
        OrderResponse mockResponse = OrderResponse.from(order);

        given(orderService.createOrder(eq(1L), any())).willReturn(mockResponse);

        String body = objectMapper.writeValueAsString(orderRequest(List.of(orderMenuRequest(1L, 2))));

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPrice").value(9000))
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.orderMenus[0].menuName").value("아메리카노"))
                .andExpect(jsonPath("$.data.orderMenus[0].subtotalPrice").value(9000));
    }

    @Test
    @DisplayName("존재하지 않는 메뉴 주문 시 404를 반환한다")
    void createOrder_menuNotFound_returns404() throws Exception {
        // given
        given(orderService.createOrder(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.MENU_NOT_FOUND));

        String body = objectMapper.writeValueAsString(orderRequest(List.of(orderMenuRequest(999L, 1))));

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.MENU_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("삭제된 메뉴 주문 시 400을 반환한다")
    void createOrder_deletedMenu_returns400() throws Exception {
        // given
        given(orderService.createOrder(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.MENU_ALREADY_DELETED));

        String body = objectMapper.writeValueAsString(orderRequest(List.of(orderMenuRequest(1L, 1))));

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.MENU_ALREADY_DELETED.getMessage()));
    }

    @Test
    @DisplayName("주문 목록이 비어 있으면 400을 반환한다")
    void createOrder_emptyOrderMenus_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(orderRequest(List.of()));

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
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
