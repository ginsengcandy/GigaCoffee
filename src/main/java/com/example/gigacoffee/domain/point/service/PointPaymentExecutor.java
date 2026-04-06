package com.example.gigacoffee.domain.point.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.common.kafka.model.event.PaymentConfirmedEvent;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 분산 락의 유효 범위 내에서 실행되는 결제 트랜잭션.
 * PointService.makePayment()가 락을 잡은 상태에서 이 메서드를 호출하고,
 * 이 메서드가 return(= TX 커밋 완료)된 이후에 락을 해제한다.
 */
@Service
@RequiredArgsConstructor
public class PointPaymentExecutor {

    private final UserPointRepository userPointRepository;
    private final PointPaymentRepository pointPaymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentEventProducer paymentEventProducer;

    @Transactional
    public PointPaymentResponse execute(Long userId, Long orderId) {
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
        List<PaymentConfirmedEvent.MenuQuantity> menuQuantities = order.getOrderMenus().stream()
                .map(orderMenu -> new PaymentConfirmedEvent.MenuQuantity(
                        orderMenu.getMenu().getId(),
                        orderMenu.getQuantity()
                ))
                .toList();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        paymentEventProducer.sendPaymentConfirmed(
                                new PaymentConfirmedEvent(userId, menuQuantities, order.getTotalPrice())
                        );
                    }
                }
        );

        return PointPaymentResponse.of(userPoint, order.getTotalPrice());
    }
}
