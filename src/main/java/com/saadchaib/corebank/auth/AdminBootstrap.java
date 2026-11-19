package com.saadchaib.corebank.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${corebank.admin.email:}") String email,
            @Value("${corebank.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return;
        }
        String normalized = AuthService.normalize(email);
        if (users.existsByEmailIgnoreCase(normalized)) {
            return;
        }
        users.save(new AppUser(normalized, passwordEncoder.encode(password), Role.ADMIN, null));
        log.info("Created admin user {}", normalized);
    }
}
