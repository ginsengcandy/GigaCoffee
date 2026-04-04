package com.example.gigacoffee.domain.point.controller;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.point.dto.PointChargeRequest;
import com.example.gigacoffee.domain.point.dto.PointChargeResponse;
import com.example.gigacoffee.domain.point.dto.PointPaymentResponse;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.service.PointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    // ========================
    // charge() - 정상 케이스
    // ========================

    @Test
    @DisplayName("정상 충전 요청 시 200과 pointBalance, chargeAmount를 반환한다")
    void charge_success() throws Exception {
        // given
        UserPoint userPoint = UserPoint.create(1L);
        ReflectionTestUtils.setField(userPoint, "pointBalance", 15000L);
        PointChargeResponse mockResponse = PointChargeResponse.of(userPoint, 10000L);

        given(pointService.charge(eq(1L), any())).willReturn(mockResponse);

        String body = objectMapper.writeValueAsString(chargeRequest(10000L));

        // when & then
        mockMvc.perform(post("/api/points/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pointBalance").value(15000))
                .andExpect(jsonPath("$.data.chargeAmount").value(10000));
    }

    // ========================
    // charge() - 유효성 검증
    // ========================

    @Test
    @DisplayName("amount 없이 요청 시 400을 반환한다")
    void charge_noAmount_returns400() throws Exception {
        // given
        String body = "{\"amount\": null}";

        // when & then
        mockMvc.perform(post("/api/points/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("amount가 0일 때 400을 반환한다")
    void charge_amountZero_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(chargeRequest(0L));

        // when & then
        mockMvc.perform(post("/api/points/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("amount가 음수일 때 400을 반환한다")
    void charge_amountNegative_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(chargeRequest(-1L));

        // when & then
        mockMvc.perform(post("/api/points/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ========================
    // charge() - 예외 케이스
    // ========================

    @Test
    @DisplayName("존재하지 않는 유저 충전 시 404를 반환한다")
    void charge_userNotFound_returns404() throws Exception {
        // given
        given(pointService.charge(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.POINT_NOT_FOUND));

        String body = objectMapper.writeValueAsString(chargeRequest(10000L));

        // when & then
        mockMvc.perform(post("/api/points/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.POINT_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("결제 처리 실패 시 500을 반환한다")
    void charge_paymentFailed_returns500() throws Exception {
        // given
        given(pointService.charge(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_FAILED));

        String body = objectMapper.writeValueAsString(chargeRequest(10000L));

        // when & then
        mockMvc.perform(post("/api/points/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.PAYMENT_FAILED.getMessage()));
    }

    // ========================
    // 헬퍼 메서드
    // ========================

    private PointChargeRequest chargeRequest(Long amount) {
        PointChargeRequest req = new PointChargeRequest();
        ReflectionTestUtils.setField(req, "amount", amount);
        return req;
    }
}
