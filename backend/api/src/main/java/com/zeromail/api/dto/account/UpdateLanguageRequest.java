package com.zeromail.api.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body for {@code PATCH /me/language}. Allow-list mirrors the database CHECK constraint on {@code
 * users.preferred_language} (Plan 01, changeset 006). Adding a third locale requires updating this
 * regex AND the DB CHECK AND CONTEXT.md decision D-B2 — keep the three sites in lock-step.
 *
 * <p>The {@link Pattern} message is intentionally a non-localized stable string; user-facing
 * localization is owned entirely by the frontend dictionary (D-D5 / D-C1: server never builds
 * localized prose).
 */
@Schema(requiredProperties = "language")
public record UpdateLanguageRequest(
        @NotBlank @Pattern(regexp = "vi|en", message = "must be vi or en") String language) {}
