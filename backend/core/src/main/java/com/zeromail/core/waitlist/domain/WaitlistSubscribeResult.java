package com.zeromail.core.waitlist.domain;

/**
 * Outcome of a waitlist subscribe attempt.
 *
 * <p>The waitlist endpoint always returns HTTP 200 with one of these codes — never 409 — to avoid
 * leaking whether an email is already registered (account-enumeration protection). The frontend
 * differentiates user-facing messages based on the returned code.
 */
public enum WaitlistSubscribeResult {

    /** New email — row inserted with status {@link WaitlistStatus#PENDING}. */
    ADDED,

    /** Email already present in {@code waitlist_email} regardless of its review status. */
    ALREADY_REGISTERED,

    /** Email already belongs to a real {@code users} row — no waitlist row was inserted. */
    ALREADY_USER
}
