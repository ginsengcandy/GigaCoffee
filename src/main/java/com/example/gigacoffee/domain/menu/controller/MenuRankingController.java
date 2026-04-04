package com.example.gigacoffee.domain.menu.controller;

import com.example.gigacoffee.common.response.ApiResponse;
import com.example.gigacoffee.domain.menu.dto.MenuRankingResponse;
import com.example.gigacoffee.domain.menu.service.MenuRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuRankingController {

    private final MenuRankingService menuRankingService;

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<MenuRankingResponse>>> getPopularMenus() {
        return ResponseEntity.ok(ApiResponse.ok(menuRankingService.getPopularMenus()));
    }
}
