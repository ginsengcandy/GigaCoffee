package com.example.gigacoffee.domain.order.controller;

import com.example.gigacoffee.common.response.ApiResponse;
import com.example.gigacoffee.common.security.SecurityUtils;
import com.example.gigacoffee.domain.menu.dto.MenuRankingResponse;
import com.example.gigacoffee.domain.menu.service.MenuRankingService;
import com.example.gigacoffee.domain.order.dto.OrderRequest;
import com.example.gigacoffee.domain.order.dto.OrderResponse;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestBody @Valid OrderRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(orderService.createOrder(userId, request)));
    }

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getRecentOrders() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(orderService.getRecentOrders(userId)));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable Long orderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        orderService.cancelOrder(userId, orderId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
