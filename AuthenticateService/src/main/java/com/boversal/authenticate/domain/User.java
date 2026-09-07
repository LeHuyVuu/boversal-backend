package com.boversal.authenticate.domain;

import java.time.OffsetDateTime;

public record User(
        long id,
        String email,
        String username,
        String passwordHash,
        String fullName,
        String phoneNumber,
        String avatarUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastLoginAt) {
}