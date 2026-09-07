package com.boversal.authenticate.features.auth.login;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.boversal.authenticate.application.AuthResult;
import com.boversal.authenticate.application.AuthenticationException;
import com.boversal.authenticate.application.PasswordHasher;
import com.boversal.authenticate.application.TokenService;
import com.boversal.authenticate.application.UserRepository;
import com.boversal.authenticate.domain.User;

@Service
public class LoginHandler {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public LoginHandler(UserRepository userRepository, PasswordHasher passwordHasher, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public AuthResult handle(LoginRequest request) {
        var email = request.email().trim().toLowerCase();
        var user = userRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);
        if (!passwordHasher.matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var loggedInUser = new User(
                user.id(), user.email(), user.username(), user.passwordHash(), user.fullName(),
                user.phoneNumber(), user.avatarUrl(), user.createdAt(), now, now);
        userRepository.save(loggedInUser);
        return AuthResult.from(loggedInUser, tokenService.create(loggedInUser));
    }

    private AuthenticationException invalidCredentials() {
        return new AuthenticationException(HttpStatus.BAD_REQUEST, "Email hoặc mật khẩu không đúng");
    }
}