package com.example.gigacoffee.domain.menu.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.menu.dto.MenuRankingResponse;
import com.example.gigacoffee.domain.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.example.gigacoffee.common.kafka.model.RedisKey.MENU_RANKING_PREFIX;

@Service
@RequiredArgsConstructor
public class MenuRankingService {

    private final StringRedisTemplate redisTemplate;
    private final MenuRepository menuRepository;

    private static final int TOP_COUNT = 3;
    private static final int DAYS_RANGE = 7;

    public List<MenuRankingResponse> getPopularMenus() {
        // 1. 최근 7일간 키 목록 생성
        List<String> keys = IntStream.range(0, DAYS_RANGE)
                .mapToObj(i -> MENU_RANKING_PREFIX + LocalDate.now().minusDays(i))
                .toList();

        // 2. 존재하는 키만 필터링
        List<String> existingKeys = keys.stream()
                .filter(key -> Boolean.TRUE.equals(redisTemplate.hasKey(key)))
                .toList();

        if (existingKeys.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 키가 1개면 바로 조회, 2개 이상이면 합산
        String targetKey;
        if (existingKeys.size() == 1) {
            targetKey = existingKeys.getFirst();
        } else {
            targetKey = MENU_RANKING_PREFIX + "aggregated:" + LocalDate.now();
            redisTemplate.opsForZSet().unionAndStore(
                    existingKeys.getFirst(),
                    existingKeys.subList(1, existingKeys.size()),
                    targetKey);
            redisTemplate.expire(targetKey, 1, TimeUnit.MINUTES);
        }

        // 4. 상위 3개 조회
        Set<String> menuIds = redisTemplate.opsForZSet()
                .reverseRange(targetKey, 0, TOP_COUNT - 1);

        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 5. 메뉴 조회
        return menuIds.stream()
                .map(id -> menuRepository.findById(Long.parseLong(id))
                        .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND)))
                .map(MenuRankingResponse::from)
                .toList();
    }
}
