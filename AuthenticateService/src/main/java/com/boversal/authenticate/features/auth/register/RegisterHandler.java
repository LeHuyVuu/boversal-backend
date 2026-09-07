package com.boversal.authenticate.features.auth.register;

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
public class RegisterHandler {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public RegisterHandler(UserRepository userRepository, PasswordHasher passwordHasher, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public AuthResult handle(RegisterRequest request) {
        var email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AuthenticationException(HttpStatus.CONFLICT, "Email đã được đăng ký");
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var user = new User(
                0,
                email,
                email,
                passwordHasher.hash(request.password()),
                request.fullName(),
                request.phoneNumber(),
                null,
                now,
                now,
                null);
        var savedUser = userRepository.save(user);
        var token = tokenService.create(savedUser);
        return AuthResult.from(savedUser, token);
    }
}