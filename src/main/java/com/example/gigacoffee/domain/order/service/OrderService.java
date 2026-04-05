package com.example.gigacoffee.domain.order.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.menu.repository.MenuRepository;
import com.example.gigacoffee.domain.order.dto.OrderRequest;
import com.example.gigacoffee.domain.order.dto.OrderResponse;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.repository.OrderRepository;
import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuRequest;
import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private static final String RECENT_ORDER_KEY_PREFIX = "orders:recent:";
    private static final long CACHE_TTL_HOURS = 1;
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(Long userId, OrderRequest request) {
        long totalPrice = 0L;
        List<Menu> menus = new ArrayList<>();

        for (OrderMenuRequest menuRequest : request.getOrderMenus()) {
            Menu menu = menuRepository.findById(menuRequest.getMenuId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));

            if (menu.isDeleted()) {
                throw new BusinessException(ErrorCode.MENU_ALREADY_DELETED);
            }

            menus.add(menu);
            totalPrice += menuRequest.getQuantity() * menu.getPrice();
        }

        Order order = Order.create(userId, totalPrice);

        for (int i = 0; i < menus.size(); i++) {
            OrderMenu orderMenu = OrderMenu.create(
                    order,
                    menus.get(i),
                    request.getOrderMenus().get(i).getQuantity()
            );
            order.getOrderMenus().add(orderMenu);
        }

        orderRepository.save(order);

        return OrderResponse.from(order);
    }

    public List<OrderResponse> getRecentOrders(Long userId) {
        String key = "orders:recent:" + userId;

        // 1. 캐시 조회
        List<String> cached = redisTemplate.opsForList().range(key, 0, 4);
        if (cached != null && !cached.isEmpty()) {
            //역직렬화 후 반환
            log.info("[Cache] 캐시 히트 - key: {}", key);
            try {
                List<OrderResponse> result = new ArrayList<>();
                for (String json : cached) {
                    result.add(objectMapper.readValue(json, OrderResponse.class));
                }
                return result;
            } catch (JacksonException e) {
                log.error("[Cache] 캐시 역직렬화 실패 - key: {}", key, e);
            }
        }

        // 2. 캐시 미스 -> DB 조회
        log.info("[Cache] 캐시 미스 - key: {}", key);
        List<Order> orders = orderRepository
                .findTop5ByUserIdOrderByCreatedAtDesc(userId);
        List<OrderResponse> result =  orders.stream()
                .map(OrderResponse::from)
                .toList();

        // 3. 캐시 저장 후 반환
        try {
            for (OrderResponse response : result) {
                redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(response));
            }
            redisTemplate.expire(key, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.info("[Cache] 캐시 저장 - key: {}", key);
        } catch (JacksonException e) {
            log.error("[Cache] 캐시 직렬화 실패 - key: {}", key, e);
        }
        return result;
    }
}
