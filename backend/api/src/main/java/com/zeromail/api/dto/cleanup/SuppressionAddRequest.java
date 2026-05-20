package com.zeromail.api.dto.cleanup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/cleanup/suppression} (UNS-02). The {@code senderEmailOrDomain}
 * is a single user-friendly input — the controller disambiguates email vs domain by presence of the
 * {@code '@'} character before constructing the underlying {@code AddSuppressionCommand}.
 */
public record SuppressionAddRequest(
        @NotBlank(message = "senderEmailOrDomain must not be blank") @Size(max = 320, message = "senderEmailOrDomain exceeds 320 characters") String senderEmailOrDomain) {}
