/**
 * Calendar REST response/request DTOs (records only, springdoc-emitted OpenAPI). Each response
 * record exposes a static {@code from(...)} factory that maps a {@code core.calendar.projection.*}
 * view into the wire shape. Request records carry Bean Validation constraints so {@code
 * GlobalExceptionHandler} returns the locked Phase 1.1 validation ProblemDetail.
 *
 * <p>{@link org.springframework.modulith.NamedInterface @NamedInterface("calendar")} re-exposes the
 * records to the {@code controllers} module so the Modulith boundary check passes (mirrors the
 * {@code dto/gmail} + {@code dto/account} convention).
 */
@org.springframework.modulith.NamedInterface("calendar")
package com.zeromail.api.dto.calendar;
