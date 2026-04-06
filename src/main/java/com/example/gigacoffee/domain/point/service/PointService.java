package com.example.gigacoffee.domain.point.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.common.payment.PaymentGateway;
import com.example.gigacoffee.common.payment.PaymentResult;
import com.example.gigacoffee.domain.point.dto.PointChargeRequest;
import com.example.gigacoffee.domain.point.dto.PointChargeResponse;
import com.example.gigacoffee.domain.point.dto.PointPaymentResponse;
import com.example.gigacoffee.domain.point.entity.PointCharge;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.enums.PointChargeType;
import com.example.gigacoffee.domain.point.repository.PointChargeRepository;
import com.example.gigacoffee.domain.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final UserPointRepository userPointRepository;
    private final RedissonClient redissonClient;
    private final PaymentGateway paymentGateway;
    private final PointChargeRepository pointChargeRepository;
    private final PointPaymentExecutor pointPaymentExecutor;

    /**
     * 분산 락으로 직렬화한 뒤, 별도 Bean(PointPaymentExecutor)의 @Transactional 메서드를 호출한다.
     * 이 메서드 자체는 @Transactional 을 갖지 않으므로, executor.execute() 가 return 하는 시점
     * (= DB 커밋 완료) 이후에 finally 의 unlock() 이 실행된다.
     * 이전 구조(락 해제가 커밋 전에 발생)에서 발생하던 이중 결제 버그가 해소된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PointPaymentResponse makePayment(Long userId, Long orderId) {
        RLock lock = redissonClient.getLock("point:lock:" + userId);

        try {
            if (!lock.tryLock(1, 3, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            return pointPaymentExecutor.execute(userId, orderId); // TX 커밋 후 return

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock(); // TX 커밋 완료 이후 락 해제
            }
        }
    }

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public PointChargeResponse charge(Long userId, PointChargeRequest request) {
        // 1. Mock 결제 실행
        PaymentResult result = paymentGateway.charge(request.getAmount());
        if(!result.isSuccess()) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        // 2. 충전 이력 저장
        PointCharge pointCharge = PointCharge.create(userId, request.getAmount(), PointChargeType.CHARGE);
        pointChargeRepository.save(pointCharge);

        // 3. 잔액 업데이트 (낙관적 락)
        UserPoint userPoint = userPointRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException((ErrorCode.POINT_NOT_FOUND)));
        userPoint.charge(request.getAmount());
        userPointRepository.save(userPoint);

        return PointChargeResponse.of(userPoint, request.getAmount());
    }
}