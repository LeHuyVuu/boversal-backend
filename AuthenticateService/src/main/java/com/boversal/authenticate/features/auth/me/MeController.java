package com.boversal.authenticate.features.auth.me;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boversal.authenticate.application.UserResponse;
import com.boversal.authenticate.web.ApiResponse;

@RestController
@RequestMapping("/api/auth/me")
public class MeController {
    private final MeHandler handler;

    public MeController(MeHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> me(
            @CookieValue(value = "jwt", required = false) String cookieToken,
            @RequestHeader(value = "X-Forwarded-Jwt", required = false) String forwardedToken,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        var token = firstNonBlank(forwardedToken, cookieToken, bearerToken(authorization));
        return ResponseEntity.ok(ApiResponse.success(handler.handle(token), "Lấy thông tin thành công"));
    }

    private String firstNonBlank(String... candidates) {
        for (var candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}