package com.example.gigacoffee.domain.point.controller;

import com.example.gigacoffee.common.response.ApiResponse;
import com.example.gigacoffee.domain.point.dto.PointChargeRequest;
import com.example.gigacoffee.domain.point.dto.PointChargeResponse;
import com.example.gigacoffee.domain.point.dto.PointPaymentResponse;
import com.example.gigacoffee.domain.point.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @PostMapping("/orders/{orderId}/payment")
    public ResponseEntity<ApiResponse<PointPaymentResponse>> makePayment(
            @PathVariable Long orderId) {
        Long userId = 1L; // 기능 테스트용 값
        return ResponseEntity.ok(ApiResponse.ok(pointService.makePayment(userId, orderId)));
    }

    @PostMapping("/points/charge")
    public ResponseEntity<ApiResponse<PointChargeResponse>> charge(
            @RequestBody @Valid PointChargeRequest request) {
        // 임시 userId = 1L (JWT 구현 후 토큰에서 추출)
        Long userId = 1L;
        return ResponseEntity.ok(ApiResponse.ok(pointService.charge(userId, request)));
    }
}
