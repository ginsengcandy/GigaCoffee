package com.example.gigacoffee.common.kafka.consumer;

import com.example.gigacoffee.common.kafka.model.event.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.example.gigacoffee.common.kafka.model.RedisKey.RECENT_ORDER_PREFIX;
import static com.example.gigacoffee.common.kafka.model.consumerGroup.ConsumerGroup.RECENT_ORDER_CONSUMER_GROUP;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecentOrderConsumer {

    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
            topics = RECENT_ORDER_PREFIX,
            groupId = RECENT_ORDER_CONSUMER_GROUP,
            containerFactory = "recentOrderKafkaListenerContainerFactory"
    )
    public void consume(PaymentConfirmedEvent event) {
        String key = RECENT_ORDER_PREFIX + event.getUserId();
        redisTemplate.delete(key);
        log.info("[RecentOrder] 최근 주문 캐시 무효화 - key: {}", key);
    }
}
