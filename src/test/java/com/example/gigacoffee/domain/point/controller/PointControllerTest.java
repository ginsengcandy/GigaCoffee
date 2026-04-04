package com.example.gigacoffee.domain.point.controller;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.point.dto.PointPaymentResponse;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.service.PointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PointController.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointService pointService;

    // ========================
    // 정상 케이스
    // ========================

    @Test
    @DisplayName("정상 결제 요청 시 200과 잔액, 결제 금액을 반환한다")
    void makePayment_success() throws Exception {
        // given
        UserPoint userPoint = UserPoint.create(1L);
        ReflectionTestUtils.setField(userPoint, "pointBalance", 5500L);
        PointPaymentResponse mockResponse = PointPaymentResponse.of(userPoint, 4500L);

        given(pointService.makePayment(eq(1L), eq(1L))).willReturn(mockResponse);

        // when & then
        mockMvc.perform(post("/api/orders/1/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pointBalance").value(5500))
                .andExpect(jsonPath("$.data.paymentAmount").value(4500));
    }

    // ========================
    // 예외 케이스
    // ========================

    @Test
    @DisplayName("존재하지 않는 주문 결제 시 404를 반환한다")
    void makePayment_orderNotFound_returns404() throws Exception {
        // given
        given(pointService.makePayment(eq(1L), eq(999L)))
                .willThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/orders/999/payment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.ORDER_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("잔액 부족 시 400을 반환한다")
    void makePayment_insufficientPoint_returns400() throws Exception {
        // given
        given(pointService.makePayment(eq(1L), eq(1L)))
                .willThrow(new BusinessException(ErrorCode.INSUFFICIENT_POINT));

        // when & then
        mockMvc.perform(post("/api/orders/1/payment"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.INSUFFICIENT_POINT.getMessage()));
    }

    @Test
    @DisplayName("이미 결제된 주문 결제 시 400을 반환한다")
    void makePayment_alreadyCompleted_returns400() throws Exception {
        // given
        given(pointService.makePayment(eq(1L), eq(1L)))
                .willThrow(new BusinessException(ErrorCode.ORDER_ALREADY_COMPLETED));

        // when & then
        mockMvc.perform(post("/api/orders/1/payment"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.ORDER_ALREADY_COMPLETED.getMessage()));
    }
}
