package com.zeromail.api.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoSentinelLeakValidator.class)
public @interface NoSentinelLeak {

    String message() default "error.admin.reason_sentinel_leak";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
