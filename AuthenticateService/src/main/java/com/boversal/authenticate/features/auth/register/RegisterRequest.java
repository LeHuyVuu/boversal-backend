package com.boversal.authenticate.features.auth.register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 20) String phoneNumber) {
}