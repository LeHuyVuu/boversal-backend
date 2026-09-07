package com.boversal.authenticate.application;

import java.util.Optional;

import com.boversal.authenticate.domain.User;

public interface UserRepository {
    Optional<User> findById(long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);
}