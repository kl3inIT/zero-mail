package com.zeromail.worker.notification;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class DigestDispatchTestData {

    private DigestDispatchTestData() {}

    static void resetDigestTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM digest_delivery");
        jdbcTemplate.execute("DELETE FROM notification_preference");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenants");
        clearDigestDispatchLock(jdbcTemplate);
        jdbcTemplate.update(
                "DELETE FROM shedlock WHERE name = ?", DigestPendingReaperJob.LOCK_NAME);
    }

    static void clearDigestDispatchLock(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "DELETE FROM shedlock WHERE name = ?", DigestDispatchScheduler.LOCK_NAME);
    }

    static UUID seedTenant(JdbcTemplate jdbcTemplate, String timeZone) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name, time_zone) VALUES (?, ?, ?)",
                tenantId,
                "digest-dispatch-" + tenantId,
                timeZone);
        return tenantId;
    }

    static void seedUser(
            JdbcTemplate jdbcTemplate, UUID tenantId, String email, String preferredLanguage) {
        jdbcTemplate.update(
                """
                INSERT INTO users(id, tenant_id, google_subject, email, onboarding_step, preferred_language)
                VALUES (?, ?, ?, ?, 'GMAIL_CONNECTED', ?)
                """,
                UUID.randomUUID(),
                tenantId,
                "google-subject-" + tenantId + "-" + UUID.randomUUID(),
                email,
                preferredLanguage);
    }

    static void seedEmailPreference(
            JdbcTemplate jdbcTemplate, UUID tenantId, boolean enabled, int sendHourLocal) {
        // digest_send_day_of_week = 3 (Wednesday, ISODOW) deliberately matches the digest tests'
        // shared REFERENCE_INSTANT 2026-05-13 (a Wednesday in Asia/Ho_Chi_Minh) so the weekly
        // dispatch predicate (EXTRACT(ISODOW) = digest_send_day_of_week) fires on the due tenant.
        jdbcTemplate.update(
                """
                INSERT INTO notification_preference(
                  id, tenant_id, channel, digest_enabled, digest_send_hour_local,
                  digest_send_day_of_week
                )
                VALUES (?, ?, 'EMAIL', ?, ?, 3)
                """,
                UUID.randomUUID(),
                tenantId,
                enabled,
                sendHourLocal);
    }

    static UUID seedDigestDelivery(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            LocalDate digestDayLocal,
            String status,
            Instant createdAt) {
        UUID deliveryId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO digest_delivery(
                  id, tenant_id, digest_day_local, status, channel, attempt_count, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'EMAIL', 1, ?, ?)
                """,
                deliveryId,
                tenantId,
                Date.valueOf(digestDayLocal),
                status,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return deliveryId;
    }

    static long deliveryCount(JdbcTemplate jdbcTemplate, UUID tenantId) {
        Long deliveryCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM digest_delivery WHERE tenant_id = ?",
                        Long.class,
                        tenantId);
        return deliveryCount == null ? 0 : deliveryCount;
    }

    static String deliveryStatus(JdbcTemplate jdbcTemplate, UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM digest_delivery WHERE tenant_id = ?", String.class, tenantId);
    }

    static String deliveryFailureReason(JdbcTemplate jdbcTemplate, UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM digest_delivery WHERE tenant_id = ?",
                String.class,
                tenantId);
    }

    static String deliveryExternalRef(JdbcTemplate jdbcTemplate, UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT external_ref FROM digest_delivery WHERE tenant_id = ?",
                String.class,
                tenantId);
    }

    static int deliveryAttemptCount(JdbcTemplate jdbcTemplate, UUID tenantId) {
        Integer attemptCount =
                jdbcTemplate.queryForObject(
                        "SELECT attempt_count FROM digest_delivery WHERE tenant_id = ?",
                        Integer.class,
                        tenantId);
        return attemptCount == null ? 0 : attemptCount;
    }

    static Instant deliveryNextAttemptAt(JdbcTemplate jdbcTemplate, UUID tenantId) {
        Timestamp nextAttemptAt =
                jdbcTemplate.queryForObject(
                        "SELECT next_attempt_at FROM digest_delivery WHERE tenant_id = ?",
                        Timestamp.class,
                        tenantId);
        return nextAttemptAt == null ? null : nextAttemptAt.toInstant();
    }

    static LocalDate deliveryDay(JdbcTemplate jdbcTemplate, UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT digest_day_local FROM digest_delivery WHERE tenant_id = ?",
                LocalDate.class,
                tenantId);
    }

    static String deliveryStatusById(JdbcTemplate jdbcTemplate, UUID deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM digest_delivery WHERE id = ?", String.class, deliveryId);
    }

    static String deliveryFailureReasonById(JdbcTemplate jdbcTemplate, UUID deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM digest_delivery WHERE id = ?",
                String.class,
                deliveryId);
    }
}
