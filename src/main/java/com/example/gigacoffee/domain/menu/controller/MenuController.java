package com.example.gigacoffee.domain.menu.controller;

import com.example.gigacoffee.common.response.ApiResponse;
import com.example.gigacoffee.domain.menu.dto.MenuResponse;
import com.example.gigacoffee.domain.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getMenus() {
        return ResponseEntity.ok(ApiResponse.ok(menuService.getMenus()));
    }
}
