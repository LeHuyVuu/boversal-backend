package com.boversal.authenticate.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.ResponseEntity;

import com.boversal.authenticate.application.AuthResult;
import com.boversal.authenticate.application.UserResponse;
import com.boversal.authenticate.features.auth.login.LoginController;
import com.boversal.authenticate.features.auth.login.LoginHandler;
import com.boversal.authenticate.features.auth.login.LoginRequest;
import com.boversal.authenticate.features.auth.logout.LogoutController;
import com.boversal.authenticate.features.auth.logout.LogoutHandler;
import com.boversal.authenticate.features.auth.me.MeController;
import com.boversal.authenticate.features.auth.me.MeHandler;
import com.boversal.authenticate.features.auth.register.RegisterController;
import com.boversal.authenticate.features.auth.register.RegisterHandler;
import com.boversal.authenticate.features.auth.register.RegisterRequest;
import com.boversal.authenticate.web.ApiResponse;

class AuthControllersTest {
    private static final UserResponse USER = new UserResponse(
            7L, "user@example.com", "Test User", null, null, OffsetDateTime.now());
    private static final AuthResult AUTH_RESULT = new AuthResult(USER, "token", OffsetDateTime.now().plusHours(1));

    @Test
    void loginDelegatesToHandlerAndReturnsSuccess() {
        LoginHandler handler = mock(LoginHandler.class);
        when(handler.handle(new LoginRequest("user@example.com", "secret"))).thenReturn(AUTH_RESULT);
        LoginController controller = new LoginController(handler);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<ApiResponse<com.boversal.authenticate.features.auth.login.LoginResponse>> response =
                controller.login(new LoginRequest("user@example.com", "secret"), servletResponse);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        verify(handler).handle(new LoginRequest("user@example.com", "secret"));
        assertThat(servletResponse.getHeader("Set-Cookie")).contains("jwt=token");
    }

    @Test
    void registerDelegatesToHandlerAndReturnsSuccess() {
        RegisterHandler handler = mock(RegisterHandler.class);
        RegisterRequest request = new RegisterRequest("new@example.com", "secret", "New User", null);
        when(handler.handle(request)).thenReturn(AUTH_RESULT);
        RegisterController controller = new RegisterController(handler);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<ApiResponse<com.boversal.authenticate.features.auth.register.RegisterResponse>> response =
                controller.register(request, servletResponse);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        verify(handler).handle(request);
        assertThat(servletResponse.getHeader("Set-Cookie")).contains("jwt=token");
    }

    @Test
    void mePrefersForwardedJwt() {
        MeHandler handler = mock(MeHandler.class);
        when(handler.handle("forwarded")).thenReturn(USER);
        MeController controller = new MeController(handler);

        ResponseEntity<ApiResponse<UserResponse>> response = controller.me("cookie", "forwarded", "Bearer authorization");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(handler).handle("forwarded");
    }

    @Test
    void meFallsBackToBearerAuthorization() {
        MeHandler handler = mock(MeHandler.class);
        when(handler.handle("authorization")).thenReturn(USER);
        MeController controller = new MeController(handler);

        controller.me(null, null, "Bearer authorization");

        verify(handler).handle("authorization");
    }

    @Test
    void logoutDelegatesAndExpiresCookie() {
        LogoutHandler handler = mock(LogoutHandler.class);
        LogoutController controller = new LogoutController(handler);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<ApiResponse<Void>> response = controller.logout(servletResponse);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(servletResponse.getHeader("Set-Cookie")).contains("jwt=").contains("Max-Age=0");
        verify(handler).handle();
    }
}
