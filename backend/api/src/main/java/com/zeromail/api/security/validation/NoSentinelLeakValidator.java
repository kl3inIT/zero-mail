package com.zeromail.api.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class NoSentinelLeakValidator implements ConstraintValidator<NoSentinelLeak, String> {

    private static final List<String> SENTINEL_PREFIXES =
            List.of("sk-", "sk-ant-", "AIza", "sk-or-");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return SENTINEL_PREFIXES.stream().noneMatch(value::contains);
    }
}
