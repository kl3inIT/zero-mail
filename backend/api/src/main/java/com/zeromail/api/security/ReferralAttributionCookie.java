package com.zeromail.api.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class ReferralAttributionCookie {

    public static final String COOKIE_NAME = "ZM_REFERRAL_CODE";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);
    private static final String VALUE_SEPARATOR = ".";

    private ReferralAttributionCookie() {}

    public record Attribution(String code, Instant attributedAt) {
        public Attribution {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code must not be blank");
            }
            Objects.requireNonNull(attributedAt, "attributedAt must not be null");
        }
    }

    public static Optional<Attribution> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .flatMap(value -> parse(value).stream())
                .findFirst();
    }

    public static void write(
            HttpServletResponse response, String code, Instant attributedAt, boolean secure) {
        ResponseCookie cookie =
                ResponseCookie.from(COOKIE_NAME, encode(code, attributedAt))
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(COOKIE_MAX_AGE)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String encode(String code, Instant attributedAt) {
        Objects.requireNonNull(attributedAt, "attributedAt must not be null");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return code + VALUE_SEPARATOR + attributedAt.toEpochMilli();
    }

    private static Optional<Attribution> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        int separatorIndex = value.lastIndexOf(VALUE_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == value.length() - 1) {
            return Optional.empty();
        }
        String code = value.substring(0, separatorIndex);
        String epochMillisText = value.substring(separatorIndex + 1);
        try {
            return Optional.of(
                    new Attribution(code, Instant.ofEpochMilli(Long.parseLong(epochMillisText))));
        } catch (IllegalArgumentException invalidCookieValue) {
            return Optional.empty();
        }
    }

    public static void clear(HttpServletResponse response, boolean secure) {
        ResponseCookie cookie =
                ResponseCookie.from(COOKIE_NAME, "")
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(Duration.ZERO)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
