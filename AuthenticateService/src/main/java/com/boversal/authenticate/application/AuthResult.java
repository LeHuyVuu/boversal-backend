package com.boversal.authenticate.application;

import java.time.OffsetDateTime;

import com.boversal.authenticate.domain.User;

public record AuthResult(UserResponse user, String token, OffsetDateTime expiresAt) {
    public static AuthResult from(User user, TokenService.Token token) {
        return new AuthResult(UserResponse.from(user), token.value(), token.expiresAt());
    }
}