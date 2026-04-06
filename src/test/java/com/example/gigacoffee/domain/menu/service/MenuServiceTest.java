package com.example.gigacoffee.domain.menu.service;

import com.example.gigacoffee.domain.menu.dto.MenuResponse;
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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.databind.type.TypeFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock private MenuRepository menuRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private TypeFactory typeFactory;
    @Mock private CollectionType listType;

    @InjectMocks
    private MenuService menuService;

    private static final String CACHE_KEY = "menus:all";

    @BeforeEach
    void setUp() {
        // 대부분의 테스트에서 사용되지만 evictMenuCache 테스트에서는 호출되지 않으므로 lenient 적용
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
        lenient().when(typeFactory.constructCollectionType(List.class, MenuResponse.class)).thenReturn(listType);
    }

    // ============================================================
    // 캐시 히트
    // ============================================================

    @Test
    @DisplayName("Redis에 캐시가 있을 때 DB 조회 없이 캐시 반환")
    void getMenus_cacheHit_returnsCachedData() {
        // given
        List<MenuResponse> cached = List.of(menuResponse(1L, "아메리카노", 4000L));
        given(valueOps.get(CACHE_KEY)).willReturn("[{\"id\":1}]");
        given(objectMapper.readValue(anyString(), eq(listType))).willReturn(cached);

        // when
        List<MenuResponse> result = menuService.getMenus();

        // then
        assertThat(result).isEqualTo(cached);
    }

    @Test
    @DisplayName("Redis에 캐시가 있을 때 menuRepository.findAllByDeletedFalse()가 호출되지 않음")
    void getMenus_cacheHit_doesNotCallRepository() {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn("[{\"id\":1}]");
        given(objectMapper.readValue(anyString(), eq(listType))).willReturn(List.of());

        // when
        menuService.getMenus();

        // then
        verify(menuRepository, never()).findAllByDeletedFalse();
    }

    // ============================================================
    // 캐시 미스
    // ============================================================

    @Test
    @DisplayName("Redis에 캐시가 없을 때 DB 조회 후 결과 반환")
    void getMenus_cacheMiss_returnsDbResult() {
        // given
        Menu menu = menuOf("아메리카노", 4000L);
        given(valueOps.get(CACHE_KEY)).willReturn(null);
        given(menuRepository.findAllByDeletedFalse()).willReturn(List.of(menu));

        // when
        List<MenuResponse> result = menuService.getMenus();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("Redis에 캐시가 없을 때 결과가 Redis에 저장됨")
    void getMenus_cacheMiss_savesToRedis() throws Exception {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn(null);
        given(menuRepository.findAllByDeletedFalse()).willReturn(List.of(menuOf("아메리카노", 4000L)));
        given(objectMapper.writeValueAsString(anyList())).willReturn("[{\"id\":1}]");

        // when
        menuService.getMenus();

        // then
        verify(valueOps).set(eq(CACHE_KEY), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("Redis에 캐시가 없을 때 TTL 1일로 저장됨")
    void getMenus_cacheMiss_savedWithOneDayTtl() throws Exception {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn(null);
        given(menuRepository.findAllByDeletedFalse()).willReturn(List.of(menuOf("아메리카노", 4000L)));
        given(objectMapper.writeValueAsString(anyList())).willReturn("[{\"id\":1}]");

        // when
        menuService.getMenus();

        // then
        verify(valueOps).set(eq(CACHE_KEY), anyString(), eq(1L), eq(TimeUnit.DAYS));
    }

    // ============================================================
    // 정상 케이스
    // ============================================================

    @Test
    @DisplayName("삭제되지 않은 메뉴만 반환됨")
    void getMenus_returnsOnlyNonDeletedMenus() {
        // given: menuRepository.findAllByDeletedFalse()는 이미 삭제된 메뉴를 제외하고 반환
        given(valueOps.get(CACHE_KEY)).willReturn(null);
        given(menuRepository.findAllByDeletedFalse()).willReturn(
                List.of(menuOf("아메리카노", 4000L), menuOf("카페라떼", 4500L))
        );

        // when
        List<MenuResponse> result = menuService.getMenus();

        // then
        assertThat(result).hasSize(2);
        verify(menuRepository).findAllByDeletedFalse();
    }

    @Test
    @DisplayName("메뉴가 없을 때 빈 리스트 반환")
    void getMenus_noMenus_returnsEmptyList() {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn(null);
        given(menuRepository.findAllByDeletedFalse()).willReturn(Collections.emptyList());

        // when
        List<MenuResponse> result = menuService.getMenus();

        // then
        assertThat(result).isEmpty();
    }

    // ============================================================
    // 캐시 무효화
    // ============================================================

    @Test
    @DisplayName("evictMenuCache() 호출 시 Redis에서 해당 키가 삭제됨")
    void evictMenuCache_deletesKeyFromRedis() {
        // when
        ReflectionTestUtils.invokeMethod(menuService, "evictMenuCache");

        // then
        verify(redisTemplate).delete(CACHE_KEY);
    }

    // ============================================================
    // 직렬화 오류
    // ============================================================

    @Test
    @DisplayName("Redis에서 꺼낸 값이 역직렬화 실패할 때 DB 조회로 폴백")
    void getMenus_deserializationFails_fallsBackToDb() {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn("[invalid json]");
        given(objectMapper.readValue(anyString(), eq(listType))).willThrow(mock(JacksonException.class));
        given(menuRepository.findAllByDeletedFalse()).willReturn(List.of(menuOf("아메리카노", 4000L)));

        // when
        List<MenuResponse> result = menuService.getMenus();

        // then: DB 조회로 폴백하여 정상 결과 반환
        assertThat(result).hasSize(1);
        verify(menuRepository).findAllByDeletedFalse();
    }

    @Test
    @DisplayName("DB 조회 결과를 Redis에 저장할 때 직렬화 실패해도 결과는 정상 반환")
    void getMenus_serializationFails_stillReturnsResult() throws Exception {
        // given
        List<Menu> menus = List.of(menuOf("아메리카노", 4000L));
        given(valueOps.get(CACHE_KEY)).willReturn(null);
        given(menuRepository.findAllByDeletedFalse()).willReturn(menus);
        given(objectMapper.writeValueAsString(anyList())).willThrow(mock(JacksonException.class));

        // when
        List<MenuResponse> result = menuService.getMenus();

        // then: 직렬화 실패와 무관하게 DB 조회 결과 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("아메리카노");
    }

    // ============================================================
    // 헬퍼 메서드
    // ============================================================

    private Menu menuOf(String name, Long price) {
        return Menu.create(name, price);
    }

    private MenuResponse menuResponse(Long id, String name, Long price) {
        Menu menu = Menu.create(name, price);
        ReflectionTestUtils.setField(menu, "id", id);
        return MenuResponse.from(menu);
    }
}
