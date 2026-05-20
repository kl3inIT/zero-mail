package com.zeromail.api.error;

import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Admin-scoped error advice. Every "domain" admin exception now extends {@link
 * com.zeromail.core.admin.shared.AdminBusinessException} (in turn {@link
 * com.zeromail.core.shared.exception.BusinessException}) so the {@code onBusinessException} handler
 * in {@link com.zeromail.api.config.GlobalExceptionHandler} translates them with the correct {@code
 * code} / {@code params} / HTTP status — no per-subclass handler needed here.
 *
 * <p>The only thing that lives here is the admin-specific elevation of {@code
 * MethodArgumentNotValidException} field-error codes into a flat top-level {@code code}, which is
 * an admin-form ergonomic convention that does not match the generic {@code
 * error.validation.field.<path>.<annotation>} shape returned by the global validation translator.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.zeromail.api.controllers.admin")
public class AdminErrorAdvice {

    private static final List<String> ELEVATED_FIELD_CODES =
            List.of("error.admin.reason_sentinel_leak", "error.admin.reason_too_short");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> onAdminValidation(MethodArgumentNotValidException exception) {
        String elevatedCode =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(fieldError -> fieldError.getDefaultMessage())
                        .filter(ELEVATED_FIELD_CODES::contains)
                        .findFirst()
                        .orElse("error.validation");
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Admin request rejected");
        problemDetail.setDetail("The admin request could not be validated.");
        problemDetail.setProperty("code", elevatedCode);
        problemDetail.setProperty("params", Map.of());
        problemDetail.setProperty("fieldErrors", List.of());
        problemDetail.setProperty("message", elevatedCode);
        return ResponseEntity.badRequest().body(problemDetail);
    }
}
