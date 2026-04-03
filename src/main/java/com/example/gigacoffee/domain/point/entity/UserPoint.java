package com.example.gigacoffee.domain.point.entity;

import com.example.gigacoffee.common.entity.BaseEntity;
import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private Long pointBalance;

    public static UserPoint create(Long userId) {
        UserPoint userPoint = new UserPoint();
        userPoint.userId = userId;
        userPoint.pointBalance = 0L;
        return userPoint;
    }

    public void charge(Long amount) {
        this.pointBalance+=amount;
    }

    public void deduct(Long amount) {
        if(this.pointBalance < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }
        this.pointBalance -= amount;
    }
}
