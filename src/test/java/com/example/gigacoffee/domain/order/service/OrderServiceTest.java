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
import com.example.gigacoffee.domain.point.entity.PointCharge;
import com.example.gigacoffee.domain.point.entity.PointPayment;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.enums.PointChargeType;
import com.example.gigacoffee.domain.point.repository.PointChargeRepository;
import com.example.gigacoffee.domain.point.repository.PointPaymentRepository;
import com.example.gigacoffee.domain.point.repository.UserPointRepository;
import com.example.gigacoffee.domain.user.repository.UserRepository;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private MenuRepository menuRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ListOperations<String, String> listOps;
    @Mock private UserRepository userRepository;
    @Mock private PointPaymentRepository pointPaymentRepository;
    @Mock private PointChargeRepository pointChargeRepository;
    @Mock private UserPointRepository userPointRepository;

    @InjectMocks
    private OrderService orderService;

    private static final Long USER_ID = 1L;
    private static final String CACHE_KEY = "orders:recent:" + USER_ID;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    // ========================
    // 정상 케이스
    // ========================

    @Test
    @DisplayName("단일 메뉴 정상 주문 생성")
    void createOrder_singleMenu_success() {
        // given
        Menu menu = Menu.create("아메리카노", 4500L);
        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 2)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getOrderMenus()).hasSize(1);
        verify(orderRepository).save(any());
    }

    @Test
    @DisplayName("복수 메뉴 정상 주문 생성")
    void createOrder_multipleMenus_success() {
        // given
        Menu americano = Menu.create("아메리카노", 4500L);
        Menu latte = Menu.create("라떼", 5000L);
        OrderRequest request = orderRequest(List.of(
                orderMenuRequest(1L, 1),
                orderMenuRequest(2L, 1)
        ));

        given(menuRepository.findById(1L)).willReturn(Optional.of(americano));
        given(menuRepository.findById(2L)).willReturn(Optional.of(latte));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getOrderMenus()).hasSize(2);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(any());
    }

    @Test
    @DisplayName("주문 생성 시 총 가격이 정확히 계산된다 (4500 × 2 + 5000 × 1 = 14000)")
    void createOrder_totalPriceCalculatedCorrectly() {
        // given
        Menu americano = Menu.create("아메리카노", 4500L);
        Menu latte = Menu.create("라떼", 5000L);
        OrderRequest request = orderRequest(List.of(
                orderMenuRequest(1L, 2),
                orderMenuRequest(2L, 1)
        ));

        given(menuRepository.findById(1L)).willReturn(Optional.of(americano));
        given(menuRepository.findById(2L)).willReturn(Optional.of(latte));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getTotalPrice()).isEqualTo(14000L);
    }

    @Test
    @DisplayName("주문 생성 시 스냅샷 컬럼(name, unitPrice)이 메뉴 현재값으로 저장된다")
    void createOrder_snapshotColumns_savedWithCurrentMenuValues() {
        // given
        Menu menu = Menu.create("아메리카노", 4500L);
        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 1)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getOrderMenus().get(0).getMenuName()).isEqualTo("아메리카노");
        assertThat(response.getOrderMenus().get(0).getUnitPrice()).isEqualTo(4500L);
    }

    // ========================
    // 메뉴 관련
    // ========================

    @Test
    @DisplayName("존재하지 않는 메뉴 주문 시 MENU_NOT_FOUND 예외를 던진다")
    void createOrder_menuNotFound_throwsException() {
        // given
        OrderRequest request = orderRequest(List.of(orderMenuRequest(999L, 1)));

        given(menuRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 메뉴 주문 시 MENU_ALREADY_DELETED 예외를 던진다")
    void createOrder_deletedMenu_throwsException() {
        // given
        Menu deletedMenu = Menu.create("아메리카노", 4500L);
        deletedMenu.delete();
        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 1)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(deletedMenu));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_ALREADY_DELETED));
    }

    @Test
    @DisplayName("존재하는 메뉴와 삭제된 메뉴 혼합 주문 시 MENU_ALREADY_DELETED 예외를 던진다")
    void createOrder_mixedMenus_throwsMenuAlreadyDeletedException() {
        // given
        Menu normalMenu = Menu.create("아메리카노", 4500L);
        Menu deletedMenu = Menu.create("라떼", 5000L);
        deletedMenu.delete();
        OrderRequest request = orderRequest(List.of(
                orderMenuRequest(1L, 1),
                orderMenuRequest(2L, 1)
        ));

        given(menuRepository.findById(1L)).willReturn(Optional.of(normalMenu));
        given(menuRepository.findById(2L)).willReturn(Optional.of(deletedMenu));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_ALREADY_DELETED));
    }

    // ========================
    // 수량 관련
    // ========================

    @Test
    @DisplayName("수량이 1일 때 정상 주문된다")
    void createOrder_quantityOne_success() {
        // given
        Menu menu = Menu.create("아메리카노", 4500L);
        OrderRequest request = orderRequest(List.of(orderMenuRequest(1L, 1)));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getTotalPrice()).isEqualTo(4500L);
        assertThat(response.getOrderMenus().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 메뉴를 여러 번 담으면 각각 별도 OrderMenu로 저장된다")
    void createOrder_duplicateMenu_savedAsSeparateOrderMenus() {
        // given
        Menu menu = Menu.create("아메리카노", 4500L);
        OrderRequest request = orderRequest(List.of(
                orderMenuRequest(1L, 1),
                orderMenuRequest(1L, 3)
        ));

        given(menuRepository.findById(1L)).willReturn(Optional.of(menu));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(1L, request);

        // then
        assertThat(response.getOrderMenus()).hasSize(2);
        assertThat(response.getOrderMenus().get(0).getQuantity()).isEqualTo(1);
        assertThat(response.getOrderMenus().get(1).getQuantity()).isEqualTo(3);
        assertThat(response.getTotalPrice()).isEqualTo(18000L); // 4500 * (1 + 3)
    }

    // ========================
    // 최근 주문 조회 - 캐시 히트
    // ========================

    @Test
    @DisplayName("Redis에 캐시가 있을 때 DB 조회 없이 캐시 반환")
    void getRecentOrders_cacheHit_returnsCachedData() throws Exception {
        // given
        OrderResponse cached = completedOrderResponseOf(1L, USER_ID, 9000L);
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(List.of("{}"));
        given(objectMapper.readValue(anyString(), eq(OrderResponse.class))).willReturn(cached);

        // when
        List<OrderResponse> result = orderService.getRecentOrders(USER_ID);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Redis에 캐시가 있을 때 orderRepository가 호출되지 않음")
    void getRecentOrders_cacheHit_doesNotCallRepository() throws Exception {
        // given
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(List.of("{}"));
        given(objectMapper.readValue(anyString(), eq(OrderResponse.class)))
                .willReturn(completedOrderResponseOf(1L, USER_ID, 9000L));

        // when
        orderService.getRecentOrders(USER_ID);

        // then
        verify(orderRepository, never())
                .findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(any(), any());
    }

    // ========================
    // 최근 주문 조회 - 캐시 미스
    // ========================

    @Test
    @DisplayName("Redis에 캐시가 없을 때 DB에서 COMPLETED 상태 주문만 조회")
    void getRecentOrders_cacheMiss_queriesDbWithCompletedStatus() throws Exception {
        // given
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(Collections.emptyList());
        given(orderRepository.findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED))
                .willReturn(List.of(completedOrderOf(1L, USER_ID, 9000L)));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        orderService.getRecentOrders(USER_ID);

        // then
        verify(orderRepository)
                .findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Redis에 캐시가 없을 때 결과가 Redis에 저장됨")
    void getRecentOrders_cacheMiss_savesToCache() throws Exception {
        // given
        List<Order> dbOrders = List.of(
                completedOrderOf(1L, USER_ID, 4500L),
                completedOrderOf(2L, USER_ID, 9000L)
        );
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(Collections.emptyList());
        given(orderRepository.findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED))
                .willReturn(dbOrders);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        orderService.getRecentOrders(USER_ID);

        // then
        verify(listOps, org.mockito.Mockito.times(2)).rightPush(eq(CACHE_KEY), anyString());
    }

    @Test
    @DisplayName("Redis에 캐시가 없을 때 TTL 1시간으로 저장됨")
    void getRecentOrders_cacheMiss_setsOneHourTtl() throws Exception {
        // given
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(Collections.emptyList());
        given(orderRepository.findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED))
                .willReturn(List.of(completedOrderOf(1L, USER_ID, 9000L)));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        orderService.getRecentOrders(USER_ID);

        // then
        verify(redisTemplate).expire(CACHE_KEY, 1L, TimeUnit.HOURS);
    }

    // ========================
    // 최근 주문 조회 - 정상 케이스
    // ========================

    @Test
    @DisplayName("주문이 5개 미만일 때 있는 것만 반환")
    void getRecentOrders_fewerThan5Orders_returnsOnlyExisting() throws Exception {
        // given
        List<Order> dbOrders = List.of(
                completedOrderOf(1L, USER_ID, 4500L),
                completedOrderOf(2L, USER_ID, 9000L)
        );
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(Collections.emptyList());
        given(orderRepository.findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED))
                .willReturn(dbOrders);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        List<OrderResponse> result = orderService.getRecentOrders(USER_ID);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("주문이 없을 때 빈 리스트 반환")
    void getRecentOrders_noOrders_returnsEmptyList() throws Exception {
        // given
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(Collections.emptyList());
        given(orderRepository.findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED))
                .willReturn(Collections.emptyList());

        // when
        List<OrderResponse> result = orderService.getRecentOrders(USER_ID);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("COMPLETED 상태 주문만 반환되는지 검증")
    void getRecentOrders_onlyCompletedOrders_returned() throws Exception {
        // given
        Order completedOrder = completedOrderOf(1L, USER_ID, 9000L);
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(Collections.emptyList());
        given(orderRepository.findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED))
                .willReturn(List.of(completedOrder));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        List<OrderResponse> result = orderService.getRecentOrders(USER_ID);

        // then
        assertThat(result).allMatch(r -> r.getOrderStatus() == OrderStatus.COMPLETED);
    }

    // ========================
    // 최근 주문 조회 - 역직렬화 오류
    // ========================

    @Test
    @DisplayName("Redis에서 꺼낸 값이 역직렬화 실패할 때 DB 조회로 폴백")
    void getRecentOrders_deserializationFailure_fallsBackToDb() throws Exception {
        // given
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(List.of("invalid-json"));
        given(objectMapper.readValue(anyString(), eq(OrderResponse.class)))
                .willThrow(mock(JacksonException.class));
        given(orderRepository.findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED))
                .willReturn(List.of(completedOrderOf(1L, USER_ID, 9000L)));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        orderService.getRecentOrders(USER_ID);

        // then
        verify(orderRepository)
                .findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("직렬화 실패해도 DB 조회 결과는 정상 반환")
    void getRecentOrders_serializationFailure_stillReturnsDbResult() throws Exception {
        // given
        Order dbOrder = completedOrderOf(1L, USER_ID, 9000L);
        given(listOps.range(CACHE_KEY, 0, 4)).willReturn(Collections.emptyList());
        given(orderRepository.findTop5ByUserIdAndOrderStatusOrderByCreatedAtDesc(USER_ID, OrderStatus.COMPLETED))
                .willReturn(List.of(dbOrder));
        given(objectMapper.writeValueAsString(any())).willThrow(mock(JacksonException.class));

        // when
        List<OrderResponse> result = orderService.getRecentOrders(USER_ID);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    // ========================
    // 주문 취소 - 정상 케이스
    // ========================

    @Test
    @DisplayName("취소 성공 시 order.cancel()이 호출된다")
    void cancelOrder_success_orderCancelCalled() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.of(pointPaymentOf(1L, 9000L)));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPointOf(0L)));

        // when
        orderService.cancelOrder(USER_ID, 1L);

        // then
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소 성공 시 REFUND 타입 PointCharge가 저장된다")
    void cancelOrder_success_refundChargeSaved() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.of(pointPaymentOf(1L, 9000L)));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPointOf(0L)));

        // when
        orderService.cancelOrder(USER_ID, 1L);

        // then
        ArgumentCaptor<PointCharge> captor = ArgumentCaptor.forClass(PointCharge.class);
        verify(pointChargeRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PointChargeType.REFUND);
    }

    @Test
    @DisplayName("취소 성공 시 포인트 잔액이 환불 금액만큼 복구된다")
    void cancelOrder_success_pointBalanceRestored() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        UserPoint userPoint = userPointOf(1000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.of(pointPaymentOf(1L, 9000L)));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));

        // when
        orderService.cancelOrder(USER_ID, 1L);

        // then
        assertThat(userPoint.getPointBalance()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("취소 성공 시 최근 주문 캐시가 삭제된다")
    void cancelOrder_success_recentOrderCacheInvalidated() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.of(pointPaymentOf(1L, 9000L)));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPointOf(0L)));

        // when
        orderService.cancelOrder(USER_ID, 1L);

        // then
        verify(redisTemplate).delete("orders:recent:" + USER_ID);
    }

    // ========================
    // 주문 취소 - 예외 케이스
    // ========================

    @Test
    @DisplayName("존재하지 않는 주문 취소 시 ORDER_NOT_FOUND 예외를 던진다")
    void cancelOrder_orderNotFound() {
        // given
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("본인 주문이 아닐 때 FORBIDDEN 예외를 던진다")
    void cancelOrder_notOwner_forbidden() {
        // given
        Long otherUserId = 2L;
        Order order = completedOrderOf(1L, otherUserId, 9000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("PENDING 상태 주문 취소 시 ORDER_NOT_CANCELLABLE 예외를 던진다")
    void cancelOrder_pendingOrder_notCancellable() {
        // given
        Order order = Order.create(USER_ID, 9000L);
        ReflectionTestUtils.setField(order, "id", 1L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE));
    }

    @Test
    @DisplayName("이미 CANCELLED 상태인 주문 취소 시 ORDER_NOT_CANCELLABLE 예외를 던진다")
    void cancelOrder_cancelledOrder_notCancellable() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        order.cancel();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE));
    }

    @Test
    @DisplayName("결제 이력이 없을 때 PAYMENT_NOT_FOUND 예외를 던진다")
    void cancelOrder_paymentNotFound() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("포인트 정보가 없을 때 POINT_NOT_FOUND 예외를 던진다")
    void cancelOrder_pointNotFound() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.of(pointPaymentOf(1L, 9000L)));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POINT_NOT_FOUND));
    }

    // ========================
    // 주문 취소 - 검증 케이스
    // ========================

    @Test
    @DisplayName("캐시 삭제 키가 orders:recent:{userId} 형식이다")
    void cancelOrder_cacheKeyFormat() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.of(pointPaymentOf(1L, 9000L)));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPointOf(0L)));

        // when
        orderService.cancelOrder(USER_ID, 1L);

        // then
        verify(redisTemplate).delete("orders:recent:1");
    }

    @Test
    @DisplayName("환불 금액은 PointPayment의 paymentAmount와 같다")
    void cancelOrder_refundAmountFromPointPayment() {
        // given
        long paymentAmount = 12000L;
        Order order = completedOrderOf(1L, USER_ID, paymentAmount);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.of(pointPaymentOf(1L, paymentAmount)));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPointOf(0L)));

        // when
        orderService.cancelOrder(USER_ID, 1L);

        // then
        ArgumentCaptor<PointCharge> captor = ArgumentCaptor.forClass(PointCharge.class);
        verify(pointChargeRepository).save(captor.capture());
        assertThat(captor.getValue().getChargeAmount()).isEqualTo(paymentAmount);
    }

    @Test
    @DisplayName("저장되는 PointCharge의 type이 REFUND다")
    void cancelOrder_refundTypeIsRefund() {
        // given
        Order order = completedOrderOf(1L, USER_ID, 9000L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(pointPaymentRepository.findByOrderId(1L)).willReturn(Optional.of(pointPaymentOf(1L, 9000L)));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPointOf(0L)));

        // when
        orderService.cancelOrder(USER_ID, 1L);

        // then
        ArgumentCaptor<PointCharge> captor = ArgumentCaptor.forClass(PointCharge.class);
        verify(pointChargeRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PointChargeType.REFUND);
    }

    // ========================
    // 헬퍼 메서드
    // ========================

    private Order completedOrderOf(Long id, Long userId, Long totalPrice) {
        Order order = Order.create(userId, totalPrice);
        order.complete();
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private OrderResponse completedOrderResponseOf(Long id, Long userId, Long totalPrice) {
        return OrderResponse.from(completedOrderOf(id, userId, totalPrice));
    }

    private PointPayment pointPaymentOf(Long orderId, Long amount) {
        PointPayment p = PointPayment.create(USER_ID, orderId, amount);
        ReflectionTestUtils.setField(p, "id", 1L);
        return p;
    }

    private UserPoint userPointOf(Long balance) {
        UserPoint up = UserPoint.create(USER_ID);
        ReflectionTestUtils.setField(up, "pointBalance", balance);
        return up;
    }

    private OrderMenuRequest orderMenuRequest(Long menuId, int quantity) {
        OrderMenuRequest req = new OrderMenuRequest();
        ReflectionTestUtils.setField(req, "menuId", menuId);
        ReflectionTestUtils.setField(req, "quantity", quantity);
        return req;
    }

    private OrderRequest orderRequest(List<OrderMenuRequest> items) {
        OrderRequest req = new OrderRequest();
        ReflectionTestUtils.setField(req, "orderMenus", items);
        return req;
    }
}
