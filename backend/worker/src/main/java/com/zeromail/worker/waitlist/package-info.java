/**
 * Worker-side waitlist invite dispatch — cron scheduler + per-row processor that polls APPROVED
 * rows in {@code waitlist_email}, renders the invite mail, and ships it through {@code
 * ResendEmailGateway}.
 */
package com.zeromail.worker.waitlist;
