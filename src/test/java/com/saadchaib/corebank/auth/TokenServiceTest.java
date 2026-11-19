package com.saadchaib.corebank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saadchaib.corebank.config.SecurityConfig;
import com.saadchaib.corebank.customer.Customer;
import com.saadchaib.corebank.security.JwtProperties;
import com.saadchaib.corebank.security.TokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

    private final SecurityConfig config = new SecurityConfig();
    private final JwtProperties properties =
            new JwtProperties("unit-test-secret-0123456789abcdef0123456789", "corebank-api", Duration.ofMinutes(15));
    private final SecretKey key = config.jwtSigningKey(properties);
    private final JwtDecoder decoder = config.jwtDecoder(key, properties);

    private TokenService serviceAt(Instant now) {
        return new TokenService(config.jwtEncoder(key), properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private AppUser user(Role role) {
        Customer customer = new Customer("Amal", "B", "amal@bank.ma", null);
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());
        AppUser user = new AppUser("amal@bank.ma", "x", role, role == Role.USER ? customer : null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void tokenCarriesSubjectRolesAndCustomer() {
        AppUser user = user(Role.USER);
        String token = serviceAt(Instant.now()).issue(user).accessToken();

        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaimAsString("customer_id")).isEqualTo(user.getCustomer().getId().toString());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void adminTokenHasNoCustomerClaim() {
        Jwt jwt = decoder.decode(serviceAt(Instant.now()).issue(user(Role.ADMIN)).accessToken());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.hasClaim("customer_id")).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(Instant.now().minus(Duration.ofHours(2))).issue(user(Role.USER)).accessToken();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = serviceAt(Instant.now()).issue(user(Role.USER)).accessToken();
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "xy." + parts[2];
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtProperties other = new JwtProperties("another-secret-0123456789abcdef0123456789", "corebank-api", Duration.ofMinutes(5));
        String token = new TokenService(config.jwtEncoder(config.jwtSigningKey(other)), other, Clock.systemUTC())
                .issue(user(Role.ADMIN)).accessToken();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "x", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
