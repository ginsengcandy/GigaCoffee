package com.example.gigacoffee.domain.menu.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.menu.dto.MenuRankingResponse;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.menu.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.gigacoffee.common.kafka.model.RedisKey.MENU_RANKING_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuRankingServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private MenuRepository menuRepository;

    @InjectMocks
    private MenuRankingService menuRankingService;

    // 서비스 내부 상수와 동일하게 맞춤
    private static final String TODAY_KEY       = MENU_RANKING_PREFIX + LocalDate.now();
    private static final String AGGREGATED_KEY  = MENU_RANKING_PREFIX + "aggregated:" + LocalDate.now();

    @BeforeEach
    void setUp() {
        // opsForZSet()은 대부분의 테스트에서 사용되지만, 데이터가 없어 즉시 반환하는 테스트에서는 호출되지 않으므로 lenient로 선언
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    // ============================================================
    // 정상 케이스
    // ============================================================

    @Test
    @DisplayName("Redis에 오늘 하루치 데이터만 있을 때 상위 3개 반환")
    void getPopularMenus_onlyTodayData_returnsTop3() {
        // given
        mockOnlyTodayKeyExists();
        given(zSetOps.reverseRange(TODAY_KEY, 0, 2)).willReturn(idSet("1", "2", "3"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));
        given(menuRepository.findById(2L)).willReturn(Optional.of(menuOf(2L, "카페라떼",  4500L)));
        given(menuRepository.findById(3L)).willReturn(Optional.of(menuOf(3L, "카푸치노",  5000L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(MenuRankingResponse::getRank).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Redis에 7일치 데이터가 있을 때 unionAndStore로 합산 후 상위 3개 반환")
    void getPopularMenus_sevenDaysData_unionsAndReturnsTop3() {
        // given
        given(redisTemplate.hasKey(anyString())).willReturn(true);
        given(zSetOps.unionAndStore(anyString(), anyCollection(), eq(AGGREGATED_KEY))).willReturn(3L);
        given(redisTemplate.expire(eq(AGGREGATED_KEY), anyLong(), any())).willReturn(true);
        given(zSetOps.reverseRange(AGGREGATED_KEY, 0, 2)).willReturn(idSet("1", "2", "3"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));
        given(menuRepository.findById(2L)).willReturn(Optional.of(menuOf(2L, "카페라떼",  4500L)));
        given(menuRepository.findById(3L)).willReturn(Optional.of(menuOf(3L, "카푸치노",  5000L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        assertThat(result).hasSize(3);
        verify(zSetOps).unionAndStore(anyString(), anyCollection(), eq(AGGREGATED_KEY));
    }

    @Test
    @DisplayName("메뉴가 3개 미만일 때 있는 것만 반환")
    void getPopularMenus_fewerThan3Menus_returnsOnlyExisting() {
        // given
        mockOnlyTodayKeyExists();
        given(zSetOps.reverseRange(TODAY_KEY, 0, 2)).willReturn(idSet("1", "2"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));
        given(menuRepository.findById(2L)).willReturn(Optional.of(menuOf(2L, "카페라떼",  4500L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("순위가 reverseRange 반환 순서(score 내림차순) 기준으로 정확히 부여됨")
    void getPopularMenus_ranksAssignedInScoreDescOrder() {
        // given
        // reverseRange는 score 높은 순으로 반환: 메뉴 3 > 메뉴 1 > 메뉴 2
        mockOnlyTodayKeyExists();
        given(zSetOps.reverseRange(TODAY_KEY, 0, 2)).willReturn(idSet("3", "1", "2"));
        given(menuRepository.findById(3L)).willReturn(Optional.of(menuOf(3L, "카푸치노",  5000L)));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));
        given(menuRepository.findById(2L)).willReturn(Optional.of(menuOf(2L, "카페라떼",  4500L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        assertThat(result.get(0).getRank()).isEqualTo(1);
        assertThat(result.get(0).getMenuId()).isEqualTo(3L);
        assertThat(result.get(1).getRank()).isEqualTo(2);
        assertThat(result.get(1).getMenuId()).isEqualTo(1L);
        assertThat(result.get(2).getRank()).isEqualTo(3);
        assertThat(result.get(2).getMenuId()).isEqualTo(2L);
    }

    // ============================================================
    // 경계값
    // ============================================================

    @Test
    @DisplayName("Redis에 데이터가 없을 때 빈 리스트 반환")
    void getPopularMenus_noDataInRedis_returnsEmptyList() {
        // given
        given(redisTemplate.hasKey(anyString())).willReturn(false);

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하는 키가 1개일 때 unionAndStore 호출 없이 해당 키로 바로 조회")
    void getPopularMenus_singleKey_doesNotCallUnionAndStore() {
        // given
        mockOnlyTodayKeyExists();
        given(zSetOps.reverseRange(TODAY_KEY, 0, 2)).willReturn(idSet("1"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));

        // when
        menuRankingService.getPopularMenus();

        // then
        verify(zSetOps, never()).unionAndStore(anyString(), anyCollection(), anyString());
        verify(zSetOps).reverseRange(TODAY_KEY, 0, 2);
    }

    @Test
    @DisplayName("존재하는 키가 2개 이상일 때 unionAndStore 호출 후 aggregated 키로 조회")
    void getPopularMenus_multipleKeys_callsUnionAndStoreAndUsesAggregatedKey() {
        // given
        String yesterdayKey = MENU_RANKING_PREFIX + LocalDate.now().minusDays(1);
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.hasKey(TODAY_KEY)).willReturn(true);
        given(redisTemplate.hasKey(yesterdayKey)).willReturn(true);
        given(zSetOps.unionAndStore(anyString(), anyCollection(), eq(AGGREGATED_KEY))).willReturn(2L);
        given(redisTemplate.expire(eq(AGGREGATED_KEY), anyLong(), any())).willReturn(true);
        given(zSetOps.reverseRange(AGGREGATED_KEY, 0, 2)).willReturn(idSet("1", "2"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));
        given(menuRepository.findById(2L)).willReturn(Optional.of(menuOf(2L, "카페라떼",  4500L)));

        // when
        menuRankingService.getPopularMenus();

        // then
        verify(zSetOps).unionAndStore(anyString(), anyCollection(), eq(AGGREGATED_KEY));
        verify(zSetOps).reverseRange(AGGREGATED_KEY, 0, 2);
    }

    @Test
    @DisplayName("오늘 기준 6일 전 데이터는 포함되고 7일 전 데이터는 조회 대상에서 제외됨")
    void getPopularMenus_sixDaysAgoIncluded_sevenDaysAgoExcluded() {
        // given
        String sixDaysAgoKey  = MENU_RANKING_PREFIX + LocalDate.now().minusDays(6);
        String sevenDaysAgoKey = MENU_RANKING_PREFIX + LocalDate.now().minusDays(7);

        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.hasKey(sixDaysAgoKey)).willReturn(true);
        given(zSetOps.reverseRange(sixDaysAgoKey, 0, 2)).willReturn(idSet("1"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then: 6일 전 데이터가 포함되어 결과 반환
        assertThat(result).hasSize(1);
        // 7일 전 키는 생성 자체가 되지 않으므로 hasKey 호출 없음
        verify(redisTemplate, never()).hasKey(sevenDaysAgoKey);
    }

    // ============================================================
    // 예외 케이스
    // ============================================================

    @Test
    @DisplayName("Redis에 menuId가 있는데 DB에 해당 메뉴가 없을 때 MENU_NOT_FOUND 예외")
    void getPopularMenus_menuNotInDb_throwsMenuNotFoundException() {
        // given
        mockOnlyTodayKeyExists();
        given(zSetOps.reverseRange(TODAY_KEY, 0, 2)).willReturn(idSet("99"));
        given(menuRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> menuRankingService.getPopularMenus())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_NOT_FOUND));
    }

    @Test
    @DisplayName("Redis에 menuId가 있는데 해당 메뉴가 soft delete된 경우 랭킹에서 제외됨")
    void getPopularMenus_softDeletedMenu_excludedFromRanking() {
        // given
        mockOnlyTodayKeyExists();
        given(zSetOps.reverseRange(TODAY_KEY, 0, 2)).willReturn(idSet("1"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(deletedMenuOf(1L, "단종메뉴", 4000L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("soft delete된 메뉴가 섞여 있을 때 살아있는 메뉴만 순위가 연속으로 부여됨")
    void getPopularMenus_mixedDeletedAndActive_onlyActiveMenusWithContinuousRanks() {
        // given: 1등(삭제됨), 2등(정상), 3등(정상) → 결과는 2개, rank 1·2
        mockOnlyTodayKeyExists();
        given(zSetOps.reverseRange(TODAY_KEY, 0, 2)).willReturn(idSet("1", "2", "3"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(deletedMenuOf(1L, "단종메뉴", 4000L)));
        given(menuRepository.findById(2L)).willReturn(Optional.of(menuOf(2L, "카페라떼",  4500L)));
        given(menuRepository.findById(3L)).willReturn(Optional.of(menuOf(3L, "카푸치노",  5000L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRank()).isEqualTo(1);
        assertThat(result.get(0).getMenuId()).isEqualTo(2L);
        assertThat(result.get(1).getRank()).isEqualTo(2);
        assertThat(result.get(1).getMenuId()).isEqualTo(3L);
    }

    // ============================================================
    // 집계 관련
    // ============================================================

    @Test
    @DisplayName("동일 메뉴가 여러 날짜에 걸쳐 주문됐을 때 unionAndStore가 호출되고 합산된 score 순서로 반환됨")
    void getPopularMenus_sameMenusAcrossMultipleDays_aggregatedByUnionAndStore() {
        // Redis 실제 ZUNIONSTORE는 각 key의 score를 합산함
        // 모킹 환경이므로 unionAndStore 호출 여부와 reverseRange 반환 순서로 검증
        // given
        String oneDayAgoKey  = MENU_RANKING_PREFIX + LocalDate.now().minusDays(1);
        String twoDaysAgoKey = MENU_RANKING_PREFIX + LocalDate.now().minusDays(2);

        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.hasKey(TODAY_KEY)).willReturn(true);
        given(redisTemplate.hasKey(oneDayAgoKey)).willReturn(true);
        given(redisTemplate.hasKey(twoDaysAgoKey)).willReturn(true);

        given(zSetOps.unionAndStore(eq(TODAY_KEY), anyCollection(), eq(AGGREGATED_KEY))).willReturn(3L);
        given(redisTemplate.expire(eq(AGGREGATED_KEY), anyLong(), any())).willReturn(true);
        // 합산 결과: 메뉴1이 3일 모두 주문됨 → 1등
        given(zSetOps.reverseRange(AGGREGATED_KEY, 0, 2)).willReturn(idSet("1", "3", "2"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));
        given(menuRepository.findById(3L)).willReturn(Optional.of(menuOf(3L, "카푸치노",  5000L)));
        given(menuRepository.findById(2L)).willReturn(Optional.of(menuOf(2L, "카페라떼",  4500L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        verify(zSetOps).unionAndStore(eq(TODAY_KEY), anyCollection(), eq(AGGREGATED_KEY));
        assertThat(result.get(0).getRank()).isEqualTo(1);
        assertThat(result.get(0).getMenuId()).isEqualTo(1L);
        assertThat(result.get(1).getRank()).isEqualTo(2);
        assertThat(result.get(1).getMenuId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("score가 동일한 메뉴가 있을 때 reverseRange 반환 순서대로 순위가 부여됨")
    void getPopularMenus_tiedScores_ranksAssignedByReturnOrder() {
        // score 동률 시 Redis는 lexicographic 순서로 반환
        // 서비스는 반환 순서 그대로 1부터 rank를 순차 부여하며, 동점 처리를 Redis에 위임함
        // given
        mockOnlyTodayKeyExists();
        given(zSetOps.reverseRange(TODAY_KEY, 0, 2)).willReturn(idSet("1", "2", "3"));
        given(menuRepository.findById(1L)).willReturn(Optional.of(menuOf(1L, "아메리카노", 4000L)));
        given(menuRepository.findById(2L)).willReturn(Optional.of(menuOf(2L, "카페라떼",  4500L)));
        given(menuRepository.findById(3L)).willReturn(Optional.of(menuOf(3L, "카푸치노",  5000L)));

        // when
        List<MenuRankingResponse> result = menuRankingService.getPopularMenus();

        // then
        assertThat(result).extracting(MenuRankingResponse::getRank).containsExactly(1, 2, 3);
        assertThat(result).extracting(MenuRankingResponse::getMenuId).containsExactly(1L, 2L, 3L);
    }

    // ============================================================
    // 헬퍼 메서드
    // ============================================================

    /** 오늘 키만 존재하고 나머지 6개 키는 존재하지 않는 상태를 모킹 */
    private void mockOnlyTodayKeyExists() {
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.hasKey(TODAY_KEY)).willReturn(true);
    }

    /** 삽입 순서를 보장하는 Set 생성 (reverseRange 반환 순서 제어용) */
    private Set<String> idSet(String... ids) {
        return new LinkedHashSet<>(List.of(ids));
    }

    private Menu menuOf(Long id, String name, Long price) {
        Menu menu = Menu.create(name, price);
        ReflectionTestUtils.setField(menu, "id", id);
        return menu;
    }

    private Menu deletedMenuOf(Long id, String name, Long price) {
        Menu menu = menuOf(id, name, price);
        menu.delete();
        return menu;
    }
}
