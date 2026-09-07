package com.boversal.authenticate.application;

import java.time.OffsetDateTime;

import com.boversal.authenticate.domain.User;

public record UserResponse(
        long id,
        String email,
        String fullName,
        String phoneNumber,
        String avatarUrl,
        OffsetDateTime createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(),
                user.email(),
                user.fullName(),
                user.phoneNumber(),
                user.avatarUrl(),
                user.createdAt());
    }
}