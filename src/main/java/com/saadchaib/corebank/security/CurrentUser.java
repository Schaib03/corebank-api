package com.saadchaib.corebank.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

public record CurrentUser(UUID userId, String email, List<String> roles, UUID customerId) {

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    public UUID requireCustomerId() {
        if (customerId == null) {
            throw new AccessDeniedException("This user has no customer profile.");
        }
        return customerId;
    }
}
