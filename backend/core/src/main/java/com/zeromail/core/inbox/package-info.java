/**
 * Gmail inbox CQRS-lite projection (Phase A foundation).
 *
 * <p>Owns the mutable per-message inbox display state ({@code gmail_inbox_projection}) and the
 * per-tenant sync cursor ({@code gmail_inbox_sync_state}) that supports backfill and future
 * incremental sync. Sensitive metadata (subject, snippet, sender email + display name) is AES-GCM
 * encrypted at the app layer through the inbox projection cipher; the sender hash column is a keyed
 * HMAC for future deterministic lookup without exposing plaintext.
 *
 * <p><b>Phase A scope:</b> schema + entities + cipher + write path from the Pub/Sub observed
 * listener + backfill plumbing (eager-on-connect + lazy-on-first-fetch via {@code processing_job}).
 *
 * <p><b>Out of scope for Phase A:</b> read swap (Phase B), thread view (own phase later),
 * sent-message projection, Gmail {@code history.list} incremental sync, cron purge of expired rows.
 * {@code mail_message_observed} remains the append-once ingestion/triage seed; this module is the
 * mutable display projection beside it, not a replacement.
 */
@org.springframework.modulith.ApplicationModule(displayName = "core.inbox")
package com.zeromail.core.inbox;
