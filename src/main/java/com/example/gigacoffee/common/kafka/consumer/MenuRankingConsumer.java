package com.example.gigacoffee.common.kafka.consumer;

import com.example.gigacoffee.common.kafka.model.event.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static com.example.gigacoffee.common.kafka.model.RedisKey.MENU_RANKING_PREFIX;
import static com.example.gigacoffee.common.kafka.model.consumerGroup.ConsumerGroup.MENU_RANKING_CONSUMER_GROUP;
import static com.example.gigacoffee.common.kafka.model.topic.KafkaTopic.TOPIC_PAYMENT_CONFIRMED;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuRankingConsumer {

    private final StringRedisTemplate redisTemplate;
    private static final long TTL_DAYS = 7;

    @KafkaListener(
            topics = TOPIC_PAYMENT_CONFIRMED,
            groupId = MENU_RANKING_CONSUMER_GROUP,
            containerFactory = "menuRankingKafkaListenerContainerFactory"
    )
    public void consume(PaymentConfirmedEvent event) {
        String todayKey = MENU_RANKING_PREFIX + LocalDate.now();

        event.getMenuQuantities().forEach(menuQuantity ->
                redisTemplate.opsForZSet().incrementScore(
                        todayKey,
                        menuQuantity.getMenuId().toString(),
                        menuQuantity.getQuantity()
                )
        );

        redisTemplate.expire(todayKey, TTL_DAYS, TimeUnit.DAYS);

        log.info("[MenuRanking] 인기 메뉴 랭킹 업데이트 - menuQuantities: {}", event.getMenuQuantities());
    }
}
