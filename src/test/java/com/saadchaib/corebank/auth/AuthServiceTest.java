package com.saadchaib.corebank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saadchaib.corebank.auth.AuthDtos.LoginRequest;
import com.saadchaib.corebank.auth.AuthDtos.RegisterRequest;
import com.saadchaib.corebank.auth.AuthDtos.TokenResponse;
import com.saadchaib.corebank.common.ConflictException;
import com.saadchaib.corebank.customer.CustomerRepository;
import com.saadchaib.corebank.security.TokenService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    AppUserRepository users;

    @Mock
    CustomerRepository customers;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    TokenService tokenService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("not-a-real-password")).thenReturn("{bcrypt}dummy");
        authService = new AuthService(users, customers, passwordEncoder, tokenService);
    }

    @Test
    void registerRejectsAnExistingEmailCaseInsensitively() {
        when(users.existsByEmailIgnoreCase("amal@bank.ma")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Amal", "B", "  Amal@Bank.MA ", "password123", null)))
                .isInstanceOf(ConflictException.class);
        verify(customers, never()).save(any());
    }

    @Test
    void loginWithUnknownEmailStillComparesAPassword() {
        when(users.findByEmailIgnoreCase("ghost@bank.ma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@bank.ma", "whatever1")))
                .isInstanceOf(BadCredentialsException.class);
        verify(passwordEncoder).matches("whatever1", "{bcrypt}dummy");
    }

    @Test
    void loginWithWrongPasswordFails() {
        AppUser user = new AppUser("amal@bank.ma", "{bcrypt}hash", Role.USER, null);
        when(users.findByEmailIgnoreCase("amal@bank.ma")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", "{bcrypt}hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("amal@bank.ma", "wrong-pass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password.");
        verify(tokenService, never()).issue(any());
    }

    @Test
    void loginIssuesAToken() {
        AppUser user = new AppUser("amal@bank.ma", "{bcrypt}hash", Role.USER, null);
        TokenResponse token = new TokenResponse("jwt", "Bearer", Instant.now());
        when(users.findByEmailIgnoreCase("amal@bank.ma")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), eq("{bcrypt}hash"))).thenReturn(true);
        when(tokenService.issue(user)).thenReturn(token);

        assertThat(authService.login(new LoginRequest("AMAL@bank.ma", "secret-pass"))).isSameAs(token);
    }
}
