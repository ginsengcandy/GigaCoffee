package com.example.gigacoffee.domain.point.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.common.payment.PaymentGateway;
import com.example.gigacoffee.common.payment.PaymentResult;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.enums.OrderStatus;
import com.example.gigacoffee.domain.order.repository.OrderRepository;
import com.example.gigacoffee.domain.point.dto.PointChargeRequest;
import com.example.gigacoffee.domain.point.dto.PointChargeResponse;
import com.example.gigacoffee.domain.point.dto.PointPaymentResponse;
import com.example.gigacoffee.domain.point.entity.PointCharge;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.enums.PointChargeType;
import com.example.gigacoffee.domain.point.producer.PaymentEventProducer;
import com.example.gigacoffee.domain.point.repository.PointChargeRepository;
import com.example.gigacoffee.domain.point.repository.PointPaymentRepository;
import com.example.gigacoffee.domain.point.repository.UserPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock private UserPointRepository userPointRepository;
    @Mock private PointPaymentRepository pointPaymentRepository;
    @Mock private PointChargeRepository pointChargeRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private PaymentEventProducer paymentEventProducer;
    @Mock private PaymentGateway paymentGateway;
    @Mock private RLock rLock;

    @InjectMocks
    private PointService pointService;

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1L;
    private static final Long PRICE = 5000L;
    private static final Long CHARGE_AMOUNT = 10000L;

    @BeforeEach
    void setUp() throws InterruptedException {
        lenient().doReturn(rLock).when(redissonClient).getLock(anyString());
        lenient().doReturn(true).when(rLock).tryLock(anyLong(), anyLong(), any());
        lenient().doReturn(true).when(rLock).isHeldByCurrentThread();
    }

    // ============================================================
    // 정상 케이스
    // ============================================================

    @Test
    @DisplayName("정상 결제 시 포인트 차감 및 결제 이력 저장")
    void makePayment_success_deductsPointAndSavesPayment() {
        // given
        Order order = pendingOrder(ORDER_ID, USER_ID, PRICE);
        UserPoint userPoint = userPointWith(USER_ID, PRICE);

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        given(pointPaymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<TransactionSynchronizationManager> ignored =
                     mockStatic(TransactionSynchronizationManager.class)) {
            // when
            PointPaymentResponse response = pointService.makePayment(USER_ID, ORDER_ID);

            // then
            assertThat(userPoint.getPointBalance()).isZero();
            assertThat(response.getPaymentAmount()).isEqualTo(PRICE);
            assertThat(response.getPointBalance()).isZero();
            verify(pointPaymentRepository).save(any());
        }
    }

    @Test
    @DisplayName("정상 결제 시 주문 상태가 COMPLETED로 변경")
    void makePayment_success_changesOrderStatusToCompleted() {
        // given
        Order order = pendingOrder(ORDER_ID, USER_ID, PRICE);
        UserPoint userPoint = userPointWith(USER_ID, PRICE);

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        given(pointPaymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<TransactionSynchronizationManager> ignored =
                     mockStatic(TransactionSynchronizationManager.class)) {
            // when
            pointService.makePayment(USER_ID, ORDER_ID);

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    @Test
    @DisplayName("정상 결제 시 트랜잭션 커밋 후 Kafka 이벤트 발행")
    void makePayment_success_publishesKafkaEventAfterCommit() {
        // given
        Order order = pendingOrder(ORDER_ID, USER_ID, PRICE);
        UserPoint userPoint = userPointWith(USER_ID, PRICE);

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        given(pointPaymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> txManager =
                     mockStatic(TransactionSynchronizationManager.class)) {
            // when
            pointService.makePayment(USER_ID, ORDER_ID);

            // then: 커밋 전에는 Kafka 이벤트가 발행되지 않음
            verifyNoInteractions(paymentEventProducer);

            // 커밋 후 afterCommit()을 직접 호출하여 이벤트 발행 검증
            txManager.verify(() ->
                    TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));

            syncCaptor.getValue().afterCommit();
            verify(paymentEventProducer).sendPaymentConfirmed(any());
        }
    }

    // ============================================================
    // 잔액 관련
    // ============================================================

    @Test
    @DisplayName("포인트 잔액이 정확히 결제 금액과 같을 때 결제 성공")
    void makePayment_exactBalance_succeeds() {
        // given
        Order order = pendingOrder(ORDER_ID, USER_ID, PRICE);
        UserPoint userPoint = userPointWith(USER_ID, PRICE); // balance == price

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        given(pointPaymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<TransactionSynchronizationManager> ignored =
                     mockStatic(TransactionSynchronizationManager.class)) {
            // when & then
            assertThatCode(() -> pointService.makePayment(USER_ID, ORDER_ID))
                    .doesNotThrowAnyException();
            assertThat(userPoint.getPointBalance()).isZero();
        }
    }

    @Test
    @DisplayName("포인트 잔액이 1원 부족할 때 INSUFFICIENT_POINT 예외")
    void makePayment_oneWonShort_throwsInsufficientPoint() {
        // given
        Order order = pendingOrder(ORDER_ID, USER_ID, PRICE);
        UserPoint userPoint = userPointWith(USER_ID, PRICE - 1);

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));

        // when & then
        assertThatThrownBy(() -> pointService.makePayment(USER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_POINT));
    }

    @Test
    @DisplayName("포인트 잔액이 0일 때 INSUFFICIENT_POINT 예외")
    void makePayment_zeroBalance_throwsInsufficientPoint() {
        // given
        Order order = pendingOrder(ORDER_ID, USER_ID, PRICE);
        UserPoint userPoint = userPointWith(USER_ID, 0L);

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));

        // when & then
        assertThatThrownBy(() -> pointService.makePayment(USER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_POINT));
    }

    // ============================================================
    // 주문 상태 관련
    // ============================================================

    @Test
    @DisplayName("이미 COMPLETED인 주문 결제 시 ORDER_ALREADY_COMPLETED 예외")
    void makePayment_completedOrder_throwsOrderAlreadyCompleted() {
        // given
        Order order = orderWithStatus(ORDER_ID, USER_ID, PRICE, OrderStatus.COMPLETED);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> pointService.makePayment(USER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_ALREADY_COMPLETED));
    }

    @Test
    @DisplayName("CANCELLED인 주문 결제 시 ORDER_ALREADY_COMPLETED 예외")
    void makePayment_cancelledOrder_throwsOrderAlreadyCompleted() {
        // given
        Order order = orderWithStatus(ORDER_ID, USER_ID, PRICE, OrderStatus.CANCELLED);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> pointService.makePayment(USER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_ALREADY_COMPLETED));
    }

    @Test
    @DisplayName("존재하지 않는 주문 결제 시 ORDER_NOT_FOUND 예외")
    void makePayment_orderNotFound_throwsOrderNotFound() {
        // given
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pointService.makePayment(USER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ============================================================
    // 포인트 관련
    // ============================================================

    @Test
    @DisplayName("존재하지 않는 유저 포인트 결제 시 POINT_NOT_FOUND 예외")
    void makePayment_userPointNotFound_throwsPointNotFound() {
        // given
        Order order = pendingOrder(ORDER_ID, USER_ID, PRICE);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pointService.makePayment(USER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POINT_NOT_FOUND));
    }

    // ============================================================
    // 동시성
    // ============================================================

    @Test
    @DisplayName("동일 유저가 동시에 두 번 결제 요청 시 하나만 성공")
    void makePayment_concurrentRequest_onlyOneSucceeds() throws Exception {
        // given
        Order order1 = pendingOrder(1L, USER_ID, PRICE);
        Order order2 = pendingOrder(2L, USER_ID, PRICE);
        UserPoint userPoint = userPointWith(USER_ID, PRICE * 2);

        // 첫 번째 tryLock만 성공 → 두 번째 스레드는 LOCK_ACQUISITION_FAILED
        AtomicBoolean lockAvailable = new AtomicBoolean(true);
        given(rLock.tryLock(anyLong(), anyLong(), any()))
                .willAnswer(inv -> lockAvailable.compareAndSet(true, false));

        lenient().doReturn(Optional.of(order1)).when(orderRepository).findById(1L);
        lenient().doReturn(Optional.of(order2)).when(orderRepository).findById(2L);
        lenient().doReturn(Optional.of(userPoint)).when(userPointRepository).findByUserId(USER_ID);
        lenient().doAnswer(inv -> inv.getArgument(0)).when(pointPaymentRepository).save(any());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger lockFailCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (long orderId = 1L; orderId <= 2L; orderId++) {
            long finalOrderId = orderId;
            executor.submit(() -> {
                startLatch.countDown();
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try (MockedStatic<TransactionSynchronizationManager> ignored =
                             mockStatic(TransactionSynchronizationManager.class)) {
                    pointService.makePayment(USER_ID, finalOrderId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.LOCK_ACQUISITION_FAILED) {
                        lockFailCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(lockFailCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("잔액이 딱 한 번만 결제 가능한 금액일 때 동시 요청 시 하나만 성공")
    void makePayment_exactBalanceForOnce_concurrentRequest_onlyOneSucceeds() throws Exception {
        // given: 잔액이 정확히 한 번만 결제 가능한 금액
        Order order1 = pendingOrder(1L, USER_ID, PRICE);
        Order order2 = pendingOrder(2L, USER_ID, PRICE);
        UserPoint userPoint = userPointWith(USER_ID, PRICE); // 딱 한 번만 결제 가능

        // Semaphore(1)로 실제 락처럼 동작 시뮬레이션:
        // → 두 스레드가 순차적으로 임계 구역을 진입, 두 번째는 잔액 부족으로 실패
        Semaphore mutex = new Semaphore(1);
        given(rLock.tryLock(anyLong(), anyLong(), any()))
                .willAnswer(inv -> mutex.tryAcquire(3, TimeUnit.SECONDS));
        doAnswer(inv -> { mutex.release(); return null; }).when(rLock).unlock();

        given(orderRepository.findById(1L)).willReturn(Optional.of(order1));
        given(orderRepository.findById(2L)).willReturn(Optional.of(order2));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        lenient().doAnswer(inv -> inv.getArgument(0)).when(pointPaymentRepository).save(any());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (long orderId = 1L; orderId <= 2L; orderId++) {
            long finalOrderId = orderId;
            executor.submit(() -> {
                startLatch.countDown();
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try (MockedStatic<TransactionSynchronizationManager> ignored =
                             mockStatic(TransactionSynchronizationManager.class)) {
                    pointService.makePayment(USER_ID, finalOrderId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.INSUFFICIENT_POINT) {
                        failCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        assertThat(userPoint.getPointBalance()).isZero();
    }

    // ============================================================
    // charge() - 정상 케이스
    // ============================================================

    @Test
    @DisplayName("정상 충전 시 포인트 잔액이 충전 금액만큼 증가한다")
    void charge_success_balanceIncreases() {
        // given
        UserPoint userPoint = userPointWith(USER_ID, 5000L);
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);

        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.success());
        given(pointChargeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        given(userPointRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        pointService.charge(USER_ID, request);

        // then
        assertThat(userPoint.getPointBalance()).isEqualTo(5000L + CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("정상 충전 시 PointCharge 이력이 CHARGE 타입으로 저장된다")
    void charge_success_savesPointChargeWithChargeType() {
        // given
        UserPoint userPoint = userPointWith(USER_ID, 0L);
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);

        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.success());
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        given(userPointRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<PointCharge> captor = ArgumentCaptor.forClass(PointCharge.class);
        given(pointChargeRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        // when
        pointService.charge(USER_ID, request);

        // then
        PointCharge saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(PointChargeType.CHARGE);
        assertThat(saved.getChargeAmount()).isEqualTo(CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("정상 충전 시 응답에 충전 후 잔액과 충전 금액이 포함된다")
    void charge_success_responseContainsBalanceAndAmount() {
        // given
        UserPoint userPoint = userPointWith(USER_ID, 5000L);
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);

        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.success());
        given(pointChargeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        given(userPointRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        PointChargeResponse response = pointService.charge(USER_ID, request);

        // then
        assertThat(response.getChargeAmount()).isEqualTo(CHARGE_AMOUNT);
        assertThat(response.getPointBalance()).isEqualTo(5000L + CHARGE_AMOUNT);
    }

    // ============================================================
    // charge() - Mock 결제 관련
    // ============================================================

    @Test
    @DisplayName("MockPaymentGateway가 실패를 반환하면 PAYMENT_FAILED 예외를 던진다")
    void charge_paymentFailed_throwsPaymentFailedException() {
        // given
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);
        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.fail());

        // when & then
        assertThatThrownBy(() -> pointService.charge(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_FAILED));
    }

    @Test
    @DisplayName("결제 실패 시 PointCharge 이력이 저장되지 않는다")
    void charge_paymentFailed_doesNotSavePointCharge() {
        // given
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);
        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.fail());

        // when
        assertThatThrownBy(() -> pointService.charge(USER_ID, request))
                .isInstanceOf(BusinessException.class);

        // then
        verify(pointChargeRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 실패 시 포인트 잔액이 변경되지 않는다")
    void charge_paymentFailed_doesNotModifyBalance() {
        // given
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);
        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.fail());

        // when
        assertThatThrownBy(() -> pointService.charge(USER_ID, request))
                .isInstanceOf(BusinessException.class);

        // then: UserPoint 조회 자체가 발생하지 않으므로 잔액 변경 불가
        verify(userPointRepository, never()).findByUserId(any());
    }

    // ============================================================
    // charge() - 포인트 관련
    // ============================================================

    @Test
    @DisplayName("존재하지 않는 유저 충전 시 POINT_NOT_FOUND 예외를 던진다")
    void charge_userPointNotFound_throwsPointNotFoundException() {
        // given
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);

        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.success());
        given(pointChargeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pointService.charge(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POINT_NOT_FOUND));
    }

    @Test
    @DisplayName("충전 후 잔액이 Long 최대값에 근접할 때 정상 동작한다")
    void charge_balanceNearLongMaxValue_succeeds() {
        // given
        long initialBalance = Long.MAX_VALUE - CHARGE_AMOUNT;
        UserPoint userPoint = userPointWith(USER_ID, initialBalance);
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);

        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.success());
        given(pointChargeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));
        given(userPointRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        PointChargeResponse response = pointService.charge(USER_ID, request);

        // then
        assertThat(response.getPointBalance()).isEqualTo(Long.MAX_VALUE);
    }

    // ============================================================
    // charge() - 낙관적 락
    // ============================================================

    @Test
    @DisplayName("동일 유저가 동시에 두 번 충전 요청 시 하나는 OptimisticLockingFailureException이 발생한다")
    void charge_concurrentRequests_oneThrowsOptimisticLockingFailureException() throws Exception {
        // given
        UserPoint userPoint = userPointWith(USER_ID, 0L);
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);

        given(paymentGateway.charge(CHARGE_AMOUNT)).willReturn(PaymentResult.success());
        given(pointChargeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userPointRepository.findByUserId(USER_ID)).willReturn(Optional.of(userPoint));

        AtomicInteger saveCount = new AtomicInteger(0);
        given(userPointRepository.save(any())).willAnswer(inv -> {
            if (saveCount.incrementAndGet() == 1) {
                return inv.getArgument(0);
            }
            throw new OptimisticLockingFailureException("version conflict");
        });

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticFailCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                startLatch.countDown();
                try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try {
                    pointService.charge(USER_ID, request);
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    optimisticFailCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(optimisticFailCount.get()).isEqualTo(1);
    }

    // ============================================================
    // 헬퍼 메서드
    // ============================================================

    private Order pendingOrder(Long id, Long userId, Long totalPrice) {
        Order order = Order.create(userId, totalPrice);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private Order orderWithStatus(Long id, Long userId, Long totalPrice, OrderStatus status) {
        Order order = pendingOrder(id, userId, totalPrice);
        ReflectionTestUtils.setField(order, "orderStatus", status);
        return order;
    }

    private UserPoint userPointWith(Long userId, Long balance) {
        UserPoint userPoint = UserPoint.create(userId);
        ReflectionTestUtils.setField(userPoint, "pointBalance", balance);
        return userPoint;
    }

    private PointChargeRequest chargeRequest(Long amount) {
        PointChargeRequest req = new PointChargeRequest();
        ReflectionTestUtils.setField(req, "amount", amount);
        return req;
    }
}
