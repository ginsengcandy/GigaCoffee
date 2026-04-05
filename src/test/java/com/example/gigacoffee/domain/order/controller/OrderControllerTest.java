package com.example.gigacoffee.domain.order.controller;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.common.security.JwtProvider;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.order.dto.OrderRequest;
import com.example.gigacoffee.domain.order.dto.OrderResponse;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.service.OrderService;
import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuRequest;
import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ========================
    // 정상 케이스
    // ========================

    @Test
    @DisplayName("정상 주문 생성 시 200과 orderId, orderStatus(PENDING), orderMenus, totalPrice를 반환한다")
    void createOrder_success() throws Exception {
        // given
        Order order = Order.create(1L, 9000L);
        ReflectionTestUtils.setField(order, "id", 1L);
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
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.orderMenus").isArray())
                .andExpect(jsonPath("$.data.orderMenus[0].menuName").value("아메리카노"))
                .andExpect(jsonPath("$.data.totalPrice").value(9000));
    }

    // ========================
    // 유효성 검증
    // ========================

    @Test
    @DisplayName("빈 orderMenus 요청 시 400을 반환한다")
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

    @Test
    @DisplayName("menuId 없이 요청 시 400을 반환한다")
    void createOrder_noMenuId_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(orderRequest(List.of(orderMenuRequest(null, 1))));

        // when & then
        mockMvc.perform(post("/api/orders")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("quantity가 0이면 400을 반환한다")
    void createOrder_quantityZero_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(orderRequest(List.of(orderMenuRequest(1L, 0))));

        // when & then
        mockMvc.perform(post("/api/orders")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("quantity가 음수이면 400을 반환한다")
    void createOrder_quantityNegative_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(orderRequest(List.of(orderMenuRequest(1L, -1))));

        // when & then
        mockMvc.perform(post("/api/orders")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ========================
    // 예외 케이스
    // ========================

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
