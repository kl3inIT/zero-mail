package com.zeromail.api.error;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * RFC 9457 {@code type} URIs for {@code ProblemDetail} responses. One stable URI per HTTP-semantic
 * category (mirrors {@link com.zeromail.core.shared.exception.ErrorClass}). Validation is broken
 * out because it shares HTTP 400 with generic bad-request but is semantically distinct (carries
 * {@code fieldErrors[]}).
 *
 * <p>URIs are opaque problem identifiers — they need not resolve. Clients SHOULD branch on the
 * dotted {@code code} field; the URI is for operator/docs disambiguation and RFC 9457 conformance.
 */
public final class ErrorTypes {

    private static final String BASE = "https://zeromail.app/problems/";

    public static final URI GENERIC = URI.create(BASE + "problem");
    public static final URI BAD_REQUEST = URI.create(BASE + "bad-request");
    public static final URI VALIDATION = URI.create(BASE + "validation");
    public static final URI UNAUTHORIZED = URI.create(BASE + "unauthorized");
    public static final URI PAYMENT_REQUIRED = URI.create(BASE + "payment-required");
    public static final URI FORBIDDEN = URI.create(BASE + "forbidden");
    public static final URI NOT_FOUND = URI.create(BASE + "not-found");
    public static final URI CONFLICT = URI.create(BASE + "conflict");
    public static final URI UNPROCESSABLE = URI.create(BASE + "unprocessable");
    public static final URI INTERNAL = URI.create(BASE + "internal");
    public static final URI GATEWAY_FAILURE = URI.create(BASE + "gateway-failure");
    public static final URI SERVICE_UNAVAILABLE = URI.create(BASE + "service-unavailable");

    /**
     * Map an HTTP status to the stable problem-type URI used by the generic {@code problem(...)}
     * helper.
     */
    public static URI forStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> BAD_REQUEST;
            case UNAUTHORIZED -> UNAUTHORIZED;
            case PAYMENT_REQUIRED -> PAYMENT_REQUIRED;
            case FORBIDDEN -> FORBIDDEN;
            case NOT_FOUND -> NOT_FOUND;
            case CONFLICT -> CONFLICT;
            case UNPROCESSABLE_CONTENT -> UNPROCESSABLE;
            case INTERNAL_SERVER_ERROR -> INTERNAL;
            case BAD_GATEWAY -> GATEWAY_FAILURE;
            case SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE;
            default -> GENERIC;
        };
    }

    private ErrorTypes() {}
}
