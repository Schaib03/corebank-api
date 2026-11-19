package com.saadchaib.corebank.auth;

import com.saadchaib.corebank.auth.AuthDtos.LoginRequest;
import com.saadchaib.corebank.auth.AuthDtos.RegisterRequest;
import com.saadchaib.corebank.auth.AuthDtos.TokenResponse;
import com.saadchaib.corebank.common.ConflictException;
import com.saadchaib.corebank.customer.Customer;
import com.saadchaib.corebank.customer.CustomerRepository;
import com.saadchaib.corebank.security.TokenService;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    // Checked when the email is unknown, so both failure paths cost one bcrypt comparison
    // and the response time does not reveal which emails are registered.
    private final String dummyHash;

    public AuthService(
            AppUserRepository users,
            CustomerRepository customers,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.users = users;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email) || customers.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        Customer customer = customers.save(
                new Customer(request.firstName().trim(), request.lastName().trim(), email, request.phone()));
        AppUser user = users.save(
                new AppUser(email, passwordEncoder.encode(request.password()), Role.USER, customer));
        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        return tokenService.issue(user);
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
