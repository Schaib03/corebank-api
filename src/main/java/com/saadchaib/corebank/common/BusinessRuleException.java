package com.saadchaib.corebank.common;

/**
 * A valid request that breaks a banking rule (insufficient funds, frozen account...).
 * The code is a stable, machine-readable identifier clients can switch on.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
