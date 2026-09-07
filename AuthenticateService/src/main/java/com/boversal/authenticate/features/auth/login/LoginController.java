package com.boversal.authenticate.features.auth.login;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boversal.authenticate.application.AuthResult;
import com.boversal.authenticate.web.ApiResponse;
import com.boversal.authenticate.web.JwtCookie;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth/login")
public class LoginController {
    private final LoginHandler handler;

    public LoginController(LoginHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResult result = handler.handle(request);
        JwtCookie.add(response, result);
        return ResponseEntity.ok(ApiResponse.success(
                new LoginResponse(result.user()), "Đăng nhập thành công"));
    }
}