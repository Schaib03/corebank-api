package com.saadchaib.corebank.account;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 16-digit account numbers: a fixed 4-digit bank prefix and 12 random digits. */
@Component
public class AccountNumberGenerator {

    static final String BANK_PREFIX = "7001";

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder number = new StringBuilder(BANK_PREFIX);
        for (int i = 0; i < 12; i++) {
            number.append(random.nextInt(10));
        }
        return number.toString();
    }
}
