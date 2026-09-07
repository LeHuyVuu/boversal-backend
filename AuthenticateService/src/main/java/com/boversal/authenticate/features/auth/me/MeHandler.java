package com.boversal.authenticate.features.auth.me;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.boversal.authenticate.application.AuthenticationException;
import com.boversal.authenticate.application.TokenService;
import com.boversal.authenticate.application.UserRepository;
import com.boversal.authenticate.application.UserResponse;

@Service
public class MeHandler {
    private final UserRepository userRepository;
    private final TokenService tokenService;

    public MeHandler(UserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    public UserResponse handle(String token) {
        var userId = tokenService.getUserId(token)
                .orElseThrow(() -> new AuthenticationException(HttpStatus.UNAUTHORIZED, "Token không hợp lệ"));
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new AuthenticationException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin user"));
    }
}