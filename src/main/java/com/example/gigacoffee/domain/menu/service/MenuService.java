package com.example.gigacoffee.domain.menu.service;

import com.example.gigacoffee.domain.menu.dto.MenuResponse;
import com.example.gigacoffee.domain.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String MENU_CACHE_KEY = "menus:all";
    private static final long CACHE_TTL_DAYS = 1;

    public List<MenuResponse> getMenus() {
        // 1. 캐시 조회
        String cached = redisTemplate.opsForValue().get(MENU_CACHE_KEY);
        if(cached != null) {
            log.info("[Cache] 캐시 히트 - key: {}", MENU_CACHE_KEY);
            try{
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, MenuResponse.class));
            } catch (JacksonException e) {
                log.error("[Cache] 캐시 역직렬화 실패 - key: {}", MENU_CACHE_KEY, e);
            }
        }
        // 2. 캐시 미스 - DB 조회
        log.info("[Cache] 캐시 미스 - key: {}", MENU_CACHE_KEY);
        List<MenuResponse> menus = menuRepository.findAllByDeletedFalse()
                .stream()
                .map(MenuResponse::from)
                .toList();

        // 3. 캐시 저장
        try {
            redisTemplate.opsForValue().set(
                    MENU_CACHE_KEY,
                    objectMapper.writeValueAsString(menus),
                    CACHE_TTL_DAYS,
                    TimeUnit.DAYS
            );
        } catch (JacksonException e) {
            log.error("[Cache] 캐시 직렬화 실패 - key: {}", MENU_CACHE_KEY, e);
        }

        return menus;
    }

    private void evictMenuCache() {
        redisTemplate.delete(MENU_CACHE_KEY);
        log.info("[Cache] 캐시 무효화 - key: {}", MENU_CACHE_KEY);
    }
}
