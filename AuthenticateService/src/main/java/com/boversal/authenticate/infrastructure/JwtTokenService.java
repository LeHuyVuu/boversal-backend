package com.boversal.authenticate.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import com.boversal.authenticate.application.TokenService;
import com.boversal.authenticate.domain.User;

@Component
public class JwtTokenService implements TokenService {
    private static final String NAME_IDENTIFIER = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier";
    private static final String EMAIL = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";
    private static final String NAME = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name";

    private final Environment environment;

    public JwtTokenService(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Token create(User user) {
        var expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(expirationHours());
        var token = Jwts.builder()
                .issuer(issuer())
                .audience().add(audience()).and()
                .id(java.util.UUID.randomUUID().toString())
                .claim(NAME_IDENTIFIER, user.id())
                .claim(EMAIL, user.email())
                .claim(NAME, user.fullName())
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt.toInstant()))
                .signWith(signingKey())
                .compact();
        return new Token(token, expiresAt);
    }

    @Override
    public Optional<Long> getUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(issuer())
                    .requireAudience(audience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.parseLong(String.valueOf(claims.get(NAME_IDENTIFIER))));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(environment.getProperty(
                "JWT_KEY", "your-super-secret-key-min-32-characters-long-12345")
                .getBytes(StandardCharsets.UTF_8));
    }

    private String issuer() {
        return environment.getProperty("JWT_ISSUER", "ProjectManagementAPI");
    }

    private String audience() {
        return environment.getProperty("JWT_AUDIENCE", "ProjectManagementClient");
    }

    private long expirationHours() {
        return Long.parseLong(environment.getProperty("JWT_EXPIRATION_HOURS", "24"));
    }
}