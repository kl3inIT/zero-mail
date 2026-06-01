/**
 * Inbox projection use-cases: cipher service, write service, backfill service.
 *
 * <p>ArchUnit boundary (Phase A Wave 4): only {@code InboxProjectionWriteService} may call the
 * native UPSERT on the projection repository, and only the Pub/Sub observed listener and the
 * backfill service may call {@code InboxProjectionWriteService.upsert}. No log statement in this
 * package may pass a plaintext field (subject / snippet / sender email / sender display name).
 */
@org.springframework.modulith.NamedInterface("usecases")
package com.zeromail.core.inbox.usecases;
