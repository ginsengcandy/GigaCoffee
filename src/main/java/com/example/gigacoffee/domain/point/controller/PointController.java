package com.example.gigacoffee.domain.point.controller;

import com.example.gigacoffee.common.response.ApiResponse;
import com.example.gigacoffee.domain.point.dto.PointPaymentResponse;
import com.example.gigacoffee.domain.point.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @PostMapping("/{orderId}/payment")
    public ResponseEntity<ApiResponse<PointPaymentResponse>> makePayment(
            @PathVariable Long orderId) {
        Long userId = 1L; // 기능 테스트용 값
        return ResponseEntity.ok(ApiResponse.ok(pointService.makePayment(userId, orderId)));
    }
}
