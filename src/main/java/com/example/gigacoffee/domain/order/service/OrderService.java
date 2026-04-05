package com.example.gigacoffee.domain.order.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.menu.entity.Menu;
import com.example.gigacoffee.domain.menu.repository.MenuRepository;
import com.example.gigacoffee.domain.order.dto.OrderRequest;
import com.example.gigacoffee.domain.order.dto.OrderResponse;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.enums.OrderStatus;
import com.example.gigacoffee.domain.order.repository.OrderRepository;
import com.example.gigacoffee.domain.orderMenu.dto.OrderMenuRequest;
import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import com.example.gigacoffee.domain.point.entity.PointCharge;
import com.example.gigacoffee.domain.point.entity.PointPayment;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.enums.PointChargeType;
import com.example.gigacoffee.domain.point.repository.PointChargeRepository;
import com.example.gigacoffee.domain.point.repository.PointPaymentRepository;
import com.example.gigacoffee.domain.point.repository.UserPointRepository;
import com.example.gigacoffee.domain.user.repository.UserRepository;
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

import static com.example.gigacoffee.common.kafka.model.RedisKey.RECENT_ORDER_PREFIX;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private static final long CACHE_TTL_HOURS = 1;
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PointPaymentRepository pointPaymentRepository;
    private final PointChargeRepository pointChargeRepository;
    private final UserPointRepository userPointRepository;

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
        String key = RECENT_ORDER_PREFIX + userId;

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
                .findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(userId, OrderStatus.COMPLETED);
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

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {

        // 1. 주문 조회
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.ORDER_NOT_FOUND)
        );

        // 2. 본인 주문 검증
        if (!order.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);

        // 3. 주문 취소 (검증은 엔티티 내부에서)
        order.cancel();

        // 4. 결제 이력에서 환불 금액 조회
        PointPayment pointPayment = pointPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 5. 환불 이력 저장
        PointCharge refund = PointCharge.create(userId, pointPayment.getPaymentAmount(), PointChargeType.REFUND);
        pointChargeRepository.save(refund);

        // 6. 포인트 잔액 복구
        UserPoint userPoint = userPointRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_NOT_FOUND));
        userPoint.charge(pointPayment.getPaymentAmount());

        // 7. 최근 주문 캐시 무효화
        redisTemplate.delete(RECENT_ORDER_PREFIX + userId);
    }
}
