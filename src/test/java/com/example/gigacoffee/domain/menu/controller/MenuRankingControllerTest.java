package com.example.gigacoffee.domain.menu.controller;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.menu.dto.MenuRankingResponse;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.menu.service.MenuRankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuRankingController.class)
class MenuRankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuRankingService menuRankingService;

    // ============================================================
    // 정상 케이스
    // ============================================================

    @Test
    @DisplayName("인기 메뉴 조회 성공 시 200 응답")
    void getPopularMenus_success_returns200() throws Exception {
        // given
        given(menuRankingService.getPopularMenus()).willReturn(top3());

        // when & then
        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("응답 바디에 rank, menuId, name, price 필드 포함")
    void getPopularMenus_success_responseContainsRequiredFields() throws Exception {
        // given
        given(menuRankingService.getPopularMenus()).willReturn(List.of(rankingResponse(1, 1L, "아메리카노", 4000L)));

        // when & then
        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].menuId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("아메리카노"))
                .andExpect(jsonPath("$.data[0].price").value(4000));
    }

    @Test
    @DisplayName("응답 데이터가 rank 오름차순으로 정렬됨")
    void getPopularMenus_success_ranksAreInAscendingOrder() throws Exception {
        // given
        given(menuRankingService.getPopularMenus()).willReturn(top3());

        // when & then
        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[2].rank").value(3));
    }

    @Test
    @DisplayName("인기 메뉴가 3개일 때 3개 모두 반환")
    void getPopularMenus_threeMenus_returnsAllThree() throws Exception {
        // given
        given(menuRankingService.getPopularMenus()).willReturn(top3());

        // when & then
        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("인기 메뉴가 3개 미만일 때 있는 것만 반환")
    void getPopularMenus_fewerThan3Menus_returnsOnlyExisting() throws Exception {
        // given
        given(menuRankingService.getPopularMenus())
                .willReturn(List.of(rankingResponse(1, 1L, "아메리카노", 4000L)));

        // when & then
        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ============================================================
    // 경계값
    // ============================================================

    @Test
    @DisplayName("Redis에 데이터가 없어 빈 리스트 반환 시 200 응답에 빈 배열 포함")
    void getPopularMenus_emptyList_returns200WithEmptyArray() throws Exception {
        // given
        given(menuRankingService.getPopularMenus()).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ============================================================
    // 예외 케이스
    // ============================================================

    @Test
    @DisplayName("메뉴 조회 중 MENU_NOT_FOUND 예외 발생 시 404 응답")
    void getPopularMenus_menuNotFound_returns404() throws Exception {
        // given
        given(menuRankingService.getPopularMenus())
                .willThrow(new BusinessException(ErrorCode.MENU_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.MENU_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("서버 내부 오류 발생 시 500 응답")
    void getPopularMenus_unexpectedException_returns500() throws Exception {
        // given
        given(menuRankingService.getPopularMenus())
                .willThrow(new RuntimeException("unexpected error"));

        // when & then
        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    // ============================================================
    // 헬퍼 메서드
    // ============================================================

    private List<MenuRankingResponse> top3() {
        return List.of(
                rankingResponse(1, 1L, "아메리카노", 4000L),
                rankingResponse(2, 2L, "카페라떼",  4500L),
                rankingResponse(3, 3L, "카푸치노",  5000L)
        );
    }

    private MenuRankingResponse rankingResponse(int rank, Long menuId, String name, Long price) {
        Menu menu = Menu.create(name, price);
        ReflectionTestUtils.setField(menu, "id", menuId);
        return MenuRankingResponse.of(rank, menu);
    }
}
