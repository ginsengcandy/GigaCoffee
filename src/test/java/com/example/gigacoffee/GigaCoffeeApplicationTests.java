package com.example.gigacoffee;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class GigaCoffeeApplicationTests {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }

}
