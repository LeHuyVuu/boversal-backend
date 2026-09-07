package com.boversal.authenticate.features.auth.logout;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boversal.authenticate.web.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth/logout")
public class LogoutController {
    private final LogoutHandler handler;

    public LogoutController(LogoutHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        handler.handle();
        response.addHeader("Set-Cookie", ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ZERO)
                .build().toString());
        return ResponseEntity.ok(ApiResponse.success(null, "Đăng xuất thành công"));
    }
}