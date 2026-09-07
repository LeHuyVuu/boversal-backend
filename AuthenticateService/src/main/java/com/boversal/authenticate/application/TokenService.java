package com.boversal.authenticate.application;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.boversal.authenticate.domain.User;

public interface TokenService {
    Token create(User user);

    Optional<Long> getUserId(String token);

    record Token(String value, OffsetDateTime expiresAt) {
    }
}