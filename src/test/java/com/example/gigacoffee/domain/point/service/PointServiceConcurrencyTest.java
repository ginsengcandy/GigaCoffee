package com.example.gigacoffee.domain.point.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.payment.PaymentGateway;
import com.example.gigacoffee.common.payment.PaymentResult;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.repository.OrderRepository;
import com.example.gigacoffee.domain.point.dto.PointChargeRequest;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.repository.PointChargeRepository;
import com.example.gigacoffee.domain.point.repository.PointPaymentRepository;
import com.example.gigacoffee.domain.point.repository.UserPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@Testcontainers
class PointServiceConcurrencyTest {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("gigacoffee")
            .withUsername("root")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.0"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.2.0").asCompatibleSubstituteFor("apache/kafka"));

    // ── Dynamic Properties ────────────────────────────────────────────────────

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                      mysql::getJdbcUrl);
        registry.add("spring.datasource.username",                 mysql::getUsername);
        registry.add("spring.datasource.password",                 mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",        () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto",              () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",    () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.data.redis.host",                     redis::getHost);
        registry.add("spring.data.redis.port",                     () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers",             kafka::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup",         () -> "false");
    }

    // ── Spring Beans ──────────────────────────────────────────────────────────

    @Autowired PointService pointService;
    @Autowired UserPointRepository userPointRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PointChargeRepository pointChargeRepository;
    @Autowired PointPaymentRepository pointPaymentRepository;

    @MockitoBean PaymentGateway paymentGateway;

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final long ORDER_PRICE   = 1_000L;
    private static final long CHARGE_AMOUNT = 1_000L;

    // 테스트 간 격리를 위한 userId
    private static final long USER_A = 1001L;
    private static final long USER_B = 1002L;
    private static final long USER_C = 1003L;
    private static final long USER_D = 1004L;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUpPaymentGateway() {
        given(paymentGateway.charge(any())).willReturn(PaymentResult.success());
    }

    @AfterEach
    void cleanUp() {
        pointPaymentRepository.deleteAll();
        pointChargeRepository.deleteAll();
        orderRepository.deleteAll();
        userPointRepository.deleteAll();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserPoint createUserPoint(long userId, long balance) {
        UserPoint up = UserPoint.create(userId);
        ReflectionTestUtils.setField(up, "pointBalance", balance);
        return userPointRepository.save(up);
    }

    private Order createPendingOrder(long userId, long price) {
        return orderRepository.save(Order.create(userId, price));
    }

    private PointChargeRequest chargeRequest(long amount) {
        PointChargeRequest req = new PointChargeRequest();
        ReflectionTestUtils.setField(req, "amount", amount);
        return req;
    }

    // ── Test A ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("분산 락: 50개 스레드가 동일 주문에 동시 결제 요청 시 하나만 성공")
    void concurrentPayment_sameOrder_onlyOneSucceeds() throws InterruptedException {
        // given
        int threadCount = 50;
        createUserPoint(USER_A, ORDER_PRICE * threadCount);
        Order order = createPendingOrder(USER_A, ORDER_PRICE);
        Long orderId = order.getId();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch startLatch  = new CountDownLatch(threadCount);
        CountDownLatch doneLatch   = new CountDownLatch(threadCount);
        ExecutorService executor   = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                startLatch.countDown();
                try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try {
                    pointService.makePayment(USER_A, orderId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
    }

    // ── Test B ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("잔액 정밀도: 100개 스레드 동시 결제, 잔액이 정확히 1회치일 때 하나만 성공하고 잔액이 음수가 되지 않음")
    void concurrentPayment_exactBalance_onlyOneSucceedsAndBalanceNonNegative() throws InterruptedException {
        // given
        int threadCount = 100;
        createUserPoint(USER_B, ORDER_PRICE); // 딱 1회 결제 가능

        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            orderIds.add(createPendingOrder(USER_B, ORDER_PRICE).getId());
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch startLatch  = new CountDownLatch(threadCount);
        CountDownLatch doneLatch   = new CountDownLatch(threadCount);
        ExecutorService executor   = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long orderId = orderIds.get(i);
            executor.submit(() -> {
                startLatch.countDown();
                try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try {
                    pointService.makePayment(USER_B, orderId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);

        long finalBalance = userPointRepository.findByUserId(USER_B)
                .map(UserPoint::getPointBalance)
                .orElse(-1L);
        assertThat(finalBalance).isGreaterThanOrEqualTo(0L);
    }

    // ── Test C ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("낙관적 락 재시도: 50개 스레드 동시 충전 시 @Retryable로 모두 성공하고 최종 잔액이 정확함")
    void concurrentCharge_allSucceedWithOptimisticLockRetry() throws InterruptedException {
        // given
        int threadCount = 50;
        createUserPoint(USER_C, 0L);
        PointChargeRequest request = chargeRequest(CHARGE_AMOUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch startLatch  = new CountDownLatch(threadCount);
        CountDownLatch doneLatch   = new CountDownLatch(threadCount);
        ExecutorService executor   = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                startLatch.countDown();
                try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try {
                    pointService.charge(USER_C, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failureCount.get()).isZero();

        long finalBalance = userPointRepository.findByUserId(USER_C)
                .map(UserPoint::getPointBalance)
                .orElse(-1L);
        assertThat(finalBalance).isEqualTo(CHARGE_AMOUNT * threadCount);
    }

    // ── Test D ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("프로모션 시나리오: 100개 스레드 동시 결제 요청 시 성공/실패 비율이 예상 범위 내")
    void concurrentPromotion_successAndFailureRatioWithinExpectedRange() throws InterruptedException {
        // given
        int threadCount = 100;
        createUserPoint(USER_D, ORDER_PRICE * threadCount);

        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            orderIds.add(createPendingOrder(USER_D, ORDER_PRICE).getId());
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch startLatch  = new CountDownLatch(threadCount);
        CountDownLatch doneLatch   = new CountDownLatch(threadCount);
        ExecutorService executor   = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            long orderId = orderIds.get(i);
            executor.submit(() -> {
                startLatch.countDown();
                try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try {
                    pointService.makePayment(USER_D, orderId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        // waitTime 1s 제한으로 일부 스레드는 LOCK_ACQUISITION_FAILED 발생 → 락 경합이 실제로 동작 중임을 검증
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(failureCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);
    }
}
