package com.boversal.authenticate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.boversal.authenticate.features.auth.login.LoginController;
import com.boversal.authenticate.features.auth.logout.LogoutController;
import com.boversal.authenticate.features.auth.me.MeController;
import com.boversal.authenticate.features.auth.register.RegisterController;
import com.boversal.authenticate.features.health.HealthController;

class ApiRouteContractTest {
    @Test
    void allAuthenticateApiControllersExposeExpectedMethods() {
        assertThat(methods(LoginController.class)).contains("POST");
        assertThat(methods(RegisterController.class)).contains("POST");
        assertThat(methods(MeController.class)).contains("GET");
        assertThat(methods(LogoutController.class)).contains("POST");
        assertThat(methods(HealthController.class)).contains("GET");
    }

    private Set<String> methods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getAnnotations()))
                .filter(annotation -> annotation instanceof GetMapping || annotation instanceof PostMapping)
                .map(annotation -> annotation instanceof GetMapping ? "GET" : "POST")
                .collect(Collectors.toSet());
    }
}
