package com.boversal.authenticate.features.auth.register;

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
@RequestMapping("/api/auth/register")
public class RegisterController {
    private final RegisterHandler handler;

    public RegisterController(RegisterHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResult result = handler.handle(request);
        JwtCookie.add(response, result);
        return ResponseEntity.ok(ApiResponse.success(
                new RegisterResponse(result.user()), "Đăng ký thành công"));
    }
}