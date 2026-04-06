package com.example.gigacoffee.domain.user.dto;

import com.example.gigacoffee.domain.user.entity.User;
import lombok.Getter;

@Getter
public class SignupResponse {

    private final Long userId;
    private final String email;
    private final String name;

    private SignupResponse(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
    }

    public static SignupResponse from(User user) {
        return new SignupResponse(user);
    }
}
