package com.example.gigacoffee.domain.user.service;

import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.common.security.JwtProvider;
import com.example.gigacoffee.domain.user.dto.LoginRequest;
import com.example.gigacoffee.domain.user.dto.LoginResponse;
import com.example.gigacoffee.domain.user.dto.SignupRequest;
import com.example.gigacoffee.domain.user.dto.SignupResponse;
import com.example.gigacoffee.domain.user.entity.User;
import com.example.gigacoffee.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = User.create(request.getEmail(), passwordEncoder.encode(request.getPassword()), request.getName());
        userRepository.save(user);
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
                if (remaining > 0) {
                    redisTemplate.opsForValue().set(
                            "blacklist:" + token,
                            "1",
                            remaining,
                            TimeUnit.MILLISECONDS
                    );
                }
            }
        });
    }
}
