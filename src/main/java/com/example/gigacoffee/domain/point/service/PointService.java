package com.example.gigacoffee.domain.point.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.common.model.kafka.event.PaymentConfirmedEvent;
import com.example.gigacoffee.domain.order.entity.Order;
import com.example.gigacoffee.domain.order.enums.OrderStatus;
import com.example.gigacoffee.domain.order.repository.OrderRepository;
import com.example.gigacoffee.domain.point.dto.PointPaymentResponse;
import com.example.gigacoffee.domain.point.entity.PointPayment;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.producer.PaymentEventProducer;
import com.example.gigacoffee.domain.point.repository.PointPaymentRepository;
import com.example.gigacoffee.domain.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final UserPointRepository userPointRepository;
    private final PointPaymentRepository pointPaymentRepository;
    private final OrderRepository orderRepository;
    private final RedissonClient redissonClient;
    private final PaymentEventProducer paymentEventProducer;

    @Transactional
    public PointPaymentResponse makePayment(Long userId, Long orderId) {
        RLock lock = redissonClient.getLock("point:lock:" + userId);

        try {
            if (!lock.tryLock(3, 3, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            // 1. 주문 조회
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

            // 2. 주문 상태 검증
            if (order.getOrderStatus() != OrderStatus.PENDING) {
                throw new BusinessException(ErrorCode.ORDER_ALREADY_COMPLETED);
            }

            // 3. 포인트 잔액 검증 및 차감
            UserPoint userPoint = userPointRepository.findByUserId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.POINT_NOT_FOUND));

            userPoint.deduct(order.getTotalPrice());

            // 4. 결제 이력 저장
            PointPayment pointPayment = PointPayment.create(
                    userId,
                    order.getId(),
                    order.getTotalPrice()
            );
            pointPaymentRepository.save(pointPayment);

            // 5. 주문 상태 변경
            order.complete();

            // 6. 트랜잭션 커밋 후 Kafka 이벤트 발행
            List<Long> menuIds = order.getOrderMenus().stream()
                    .map(orderMenu -> orderMenu.getMenu().getId())
                    .toList();

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            paymentEventProducer.sendPaymentConfirmed(
                                    new PaymentConfirmedEvent(userId, menuIds, order.getTotalPrice())
                            );
                        }
                    }
            );

            return PointPaymentResponse.of(userPoint, order.getTotalPrice());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}