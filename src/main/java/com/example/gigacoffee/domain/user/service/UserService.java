package com.example.gigacoffee.domain.user.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.common.security.JwtProvider;
import com.example.gigacoffee.domain.point.entity.UserPoint;
import com.example.gigacoffee.domain.point.repository.UserPointRepository;
import com.example.gigacoffee.domain.user.dto.LoginRequest;
import com.example.gigacoffee.domain.user.dto.LoginResponse;
import com.example.gigacoffee.domain.user.dto.SignupRequest;
import com.example.gigacoffee.domain.user.dto.SignupResponse;
import com.example.gigacoffee.domain.user.entity.User;
import com.example.gigacoffee.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final UserPointRepository userPointRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // 1. 유저 생성
        User user = User.create(request.getEmail(), passwordEncoder.encode(request.getPassword()), request.getName());
        userRepository.save(user);

        // 2. 포인트 잔액 초기화
        UserPoint userPoint = UserPoint.create(user.getId());
        userPointRepository.save(userPoint);

        return SignupResponse.from(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_MISMATCH));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        return new LoginResponse(jwtProvider.generateToken(user.getId(), user.getRole()));
    }

    @Transactional
    public void deleteUser(Long userId, String token) {
        // fail-closed: Redis 장애 시 탈퇴 거부 (토큰 블랙리스트 등록 보장 불가)
        try {
            redisTemplate.hasKey("health:check");
        } catch (Exception e) {
            log.error("[Redis] 연결 오류 - fail-closed 처리", e);
            throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
        }

        user.delete();

        // 트랜잭션 커밋 후 Redis 블랙리스트 등록 (TTL = 토큰 잔여 만료 시간)
        long remaining = jwtProvider.getRemainingExpiration(token);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (remaining > 0) {
                        redisTemplate.opsForValue().set(
                                "blacklist:" + token,
                                "1",
                                remaining,
                                TimeUnit.MILLISECONDS
                        );
                    }
                } catch (Exception e) {
                    log.error("[Redis] 블랙리스트 등록 실패 - token이 유효 기간 동안 재사용될 수 있음", e);
                }
            }
        });
    }
}
