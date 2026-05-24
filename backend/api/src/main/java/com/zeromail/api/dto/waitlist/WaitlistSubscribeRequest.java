package com.zeromail.api.dto.waitlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/waitlist/subscribe}. Public endpoint — Bean Validation runs before the
 * controller handler so format errors surface as RFC 7807 400 responses via the global handler.
 *
 * <p>The {@code website} field is a honeypot for bot detection: a real frontend leaves it blank. If
 * a submission carries any non-blank value the server silently accepts the request (returns 200
 * with {@code ADDED}) without writing to the database — making waitlist scraping look
 * indistinguishable from a successful signup for the bot.
 */
@Schema(requiredProperties = "email")
public record WaitlistSubscribeRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 64) String source,
        @Schema(description = "Honeypot field. Must be empty.", hidden = true) String website) {}
