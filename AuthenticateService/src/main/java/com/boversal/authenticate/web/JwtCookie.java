package com.boversal.authenticate.web;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.http.ResponseCookie;

import com.boversal.authenticate.application.AuthResult;

import jakarta.servlet.http.HttpServletResponse;

public final class JwtCookie {
    private JwtCookie() {
    }

    public static void add(HttpServletResponse response, AuthResult result) {
        var maxAge = Duration.between(OffsetDateTime.now(), result.expiresAt());
        response.addHeader("Set-Cookie", ResponseCookie.from("jwt", result.token())
                .httpOnly(true)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build().toString());
    }
}