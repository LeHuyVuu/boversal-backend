package com.boversal.authenticate.infrastructure.persistence;

import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.boversal.authenticate.application.UserRepository;
import com.boversal.authenticate.domain.User;

@Repository
public class DatabaseUserRepository implements UserRepository {
    private final SpringDataUserRepository repository;

    public DatabaseUserRepository(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findById(long id) {
        return repository.findById(id).map(DatabaseUserRepository::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).map(DatabaseUserRepository::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    @Override
    public User save(User user) {
        var entity = user.id() == 0
                ? new UserEntity(
                        user.email(),
                        user.username(),
                        user.passwordHash(),
                        user.fullName(),
                        user.phoneNumber(),
                        user.createdAt().toLocalDateTime(),
                        user.updatedAt().toLocalDateTime())
                : repository.findById(user.id()).orElseThrow();

        if (user.id() != 0) {
            entity.updateLastLogin(user.lastLoginAt() == null
                    ? user.updatedAt().toLocalDateTime()
                    : user.lastLoginAt().toLocalDateTime());
        }

        return toDomain(repository.save(entity));
    }

    private static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.getPhoneNumber(),
                entity.getAvatarUrl(),
                entity.getCreatedAt().atOffset(ZoneOffset.UTC),
                entity.getUpdatedAt().atOffset(ZoneOffset.UTC),
                entity.getLastLoginAt() == null ? null : entity.getLastLoginAt().atOffset(ZoneOffset.UTC));
    }
}