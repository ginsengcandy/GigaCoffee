package com.example.gigacoffee.domain.user.controller;

import com.example.gigacoffee.common.response.ApiResponse;
import com.example.gigacoffee.common.security.SecurityUtils;
import com.example.gigacoffee.domain.user.dto.LoginRequest;
import com.example.gigacoffee.domain.user.dto.LoginResponse;
import com.example.gigacoffee.domain.user.dto.SignupRequest;
import com.example.gigacoffee.domain.user.dto.SignupResponse;
import com.example.gigacoffee.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestBody @Valid SignupRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.login(request)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteUser(HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String token = extractToken(request);
        userService.deleteUser(userId, token);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : null;
    }
}
