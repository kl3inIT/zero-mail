package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UNS-02 — Suppression list CRUD + auto-add heuristic stub.
 *
 * <p>Future production classes (Wave 2 / Plan 04):
 *
 * <ul>
 *   <li>{@code SuppressionCrudService} — manual add/remove of {@code (sender_email | sender_domain,
 *       reason, created_at)}, exactly one of {@code sender_email} / {@code sender_domain} NOT NULL
 *       (CHECK constraint enforced at DB layer).
 *   <li>{@code SuppressionAutoAddService} — heuristic: if user replied to {@code sender_email} ≥1
 *       time in the last 90 days, auto-insert with {@code reason='replied'}.
 * </ul>
 *
 * <p>Wave 0 RED: production classes not present → {@code Class.forName(...)} throws.
 */
@SuppressWarnings("SqlResolve")
class SuppressionServiceTest extends PostgresContainerTest {

    private static final String SUPPRESSION_CRUD_SERVICE =
            "com.zeromail.core.cleanup.usecases.SuppressionCrudService";
    private static final String SUPPRESSION_AUTO_ADD_SERVICE =
            "com.zeromail.core.cleanup.usecases.SuppressionAutoAddService";

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void future_suppression_service_types_are_present() {
        assertThatCode(() -> Class.forName(SUPPRESSION_CRUD_SERVICE))
                .as("Future production type must exist: " + SUPPRESSION_CRUD_SERVICE)
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(SUPPRESSION_AUTO_ADD_SERVICE))
                .as("Future production type must exist: " + SUPPRESSION_AUTO_ADD_SERVICE)
                .doesNotThrowAnyException();
    }

    @Test
    void addSenderEmail_isPersisted() throws Exception {
        Class.forName(SUPPRESSION_CRUD_SERVICE);
        UUID tenantId = seedTenant();
        Object suppressionCrudService = lookupSuppressionCrudBean();

        UUID suppressionId =
                (UUID)
                        withTenant(
                                tenantId,
                                () ->
                                        invokeAddSenderEmail(
                                                suppressionCrudService,
                                                tenantId,
                                                "boss@example.com",
                                                "manual"));

        Long rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from sender_suppression where id = ?",
                        Long.class,
                        suppressionId);
        assertThat(rowCount).as("row must be persisted").isEqualTo(1L);
    }

    @Test
    void addSenderDomain_isPersisted() throws Exception {
        Class.forName(SUPPRESSION_CRUD_SERVICE);
        UUID tenantId = seedTenant();
        Object suppressionCrudService = lookupSuppressionCrudBean();

        UUID suppressionId =
                (UUID)
                        withTenant(
                                tenantId,
                                () ->
                                        invokeAddSenderDomain(
                                                suppressionCrudService,
                                                tenantId,
                                                "blocked.test",
                                                "manual"));

        Long rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from sender_suppression where id = ?",
                        Long.class,
                        suppressionId);
        assertThat(rowCount).as("row must be persisted").isEqualTo(1L);
    }

    @Test
    void add_rejectsBothNull() throws Exception {
        Class.forName(SUPPRESSION_CRUD_SERVICE);
        UUID tenantId = seedTenant();
        Object suppressionCrudService = lookupSuppressionCrudBean();

        assertThatThrownBy(
                        () ->
                                withTenant(
                                        tenantId,
                                        () ->
                                                invokeAddBoth(
                                                        suppressionCrudService,
                                                        tenantId,
                                                        null,
                                                        null,
                                                        "manual")))
                .as("must reject when both sender_email and sender_domain are null")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void add_rejectsBothNonNull() throws Exception {
        Class.forName(SUPPRESSION_CRUD_SERVICE);
        UUID tenantId = seedTenant();
        Object suppressionCrudService = lookupSuppressionCrudBean();

        assertThatThrownBy(
                        () ->
                                withTenant(
                                        tenantId,
                                        () ->
                                                invokeAddBoth(
                                                        suppressionCrudService,
                                                        tenantId,
                                                        "boss@example.com",
                                                        "example.com",
                                                        "manual")))
                .as("CHECK constraint must reject when both columns are set")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void remove_byId_softCheckRowAbsent() throws Exception {
        Class.forName(SUPPRESSION_CRUD_SERVICE);
        UUID tenantId = seedTenant();
        Object suppressionCrudService = lookupSuppressionCrudBean();

        UUID suppressionId =
                (UUID)
                        withTenant(
                                tenantId,
                                () ->
                                        invokeAddSenderEmail(
                                                suppressionCrudService,
                                                tenantId,
                                                "removable@example.com",
                                                "manual"));
        withTenant(
                tenantId, () -> invokeRemoveById(suppressionCrudService, tenantId, suppressionId));

        Long rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from sender_suppression where id = ?",
                        Long.class,
                        suppressionId);
        assertThat(rowCount).as("row must be removed").isZero();
    }

    @Test
    void autoAddAfterUserRepliedOnce_within90d_appearsAsRepliedReason() throws Exception {
        Class.forName(SUPPRESSION_AUTO_ADD_SERVICE);
        UUID tenantId = seedTenant();
        String repliedSenderEmail = "newsletter@replied.test";
        seedUserReplyAudit(tenantId, repliedSenderEmail, Instant.parse("2026-04-01T00:00:00Z"));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);

        Object suppressionAutoAddService = lookupSuppressionAutoAddBean(fixedClock);
        withTenant(tenantId, () -> invokeRunAutoAddCycle(suppressionAutoAddService, tenantId));

        String reason =
                jdbcTemplate.queryForObject(
                        """
                        select reason from sender_suppression
                        where tenant_id = ? and sender_email = ?
                        """,
                        String.class,
                        tenantId,
                        repliedSenderEmail);
        assertThat(reason).as("auto-add must set reason='replied'").isEqualTo("replied");
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "suppression-" + tenantId);
        return tenantId;
    }

    private void seedUserReplyAudit(UUID tenantId, String senderEmail, Instant repliedAt) {
        // Wave 2 implementation will scan triage_audit for SAVE_DRAFT/sent reactions to a sender;
        // the exact schema is shipped in Plan 02. This stub seeds a placeholder row so the test
        // describes intent even though the real auto-add scan is not yet implemented.
        jdbcTemplate.update(
                """
                insert into triage_audit(
                    audit_id, tenant_id, gmail_message_id, gmail_thread_id, subject_excerpt,
                    sanitized_sender_email, rule_id, reason, action_type, args_hash,
                    action_args_json, matcher_evidence, decision, created_at, decided_at,
                    attempt_count)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                "reply-source",
                "reply-thread",
                "Subject",
                senderEmail,
                null,
                "user replied",
                "SAVE_DRAFT",
                new byte[32],
                "{\"type\":\"save_draft\"}",
                "n/a",
                "APPLIED",
                repliedAt,
                repliedAt,
                1);
    }

    private Object lookupSuppressionCrudBean() throws Exception {
        return Class.forName(SUPPRESSION_CRUD_SERVICE).getDeclaredConstructor().newInstance();
    }

    private Object lookupSuppressionAutoAddBean(Clock fixedClock) throws Exception {
        return Class.forName(SUPPRESSION_AUTO_ADD_SERVICE)
                .getDeclaredConstructor(Clock.class)
                .newInstance(fixedClock);
    }

    private static Object invokeAddSenderEmail(
            Object suppressionCrudService, UUID tenantId, String senderEmail, String reason) {
        try {
            return suppressionCrudService
                    .getClass()
                    .getMethod("addSenderEmail", UUID.class, String.class, String.class)
                    .invoke(suppressionCrudService, tenantId, senderEmail, reason);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }

    private static Object invokeAddSenderDomain(
            Object suppressionCrudService, UUID tenantId, String senderDomain, String reason) {
        try {
            return suppressionCrudService
                    .getClass()
                    .getMethod("addSenderDomain", UUID.class, String.class, String.class)
                    .invoke(suppressionCrudService, tenantId, senderDomain, reason);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }

    private static Object invokeAddBoth(
            Object suppressionCrudService,
            UUID tenantId,
            String senderEmail,
            String senderDomain,
            String reason) {
        try {
            return suppressionCrudService
                    .getClass()
                    .getMethod("add", UUID.class, String.class, String.class, String.class)
                    .invoke(suppressionCrudService, tenantId, senderEmail, senderDomain, reason);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }

    private static Object invokeRemoveById(
            Object suppressionCrudService, UUID tenantId, UUID suppressionId) {
        try {
            return suppressionCrudService
                    .getClass()
                    .getMethod("removeById", UUID.class, UUID.class)
                    .invoke(suppressionCrudService, tenantId, suppressionId);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }

    private static Object invokeRunAutoAddCycle(Object suppressionAutoAddService, UUID tenantId) {
        try {
            return suppressionAutoAddService
                    .getClass()
                    .getMethod("runAutoAddCycle", UUID.class)
                    .invoke(suppressionAutoAddService, tenantId);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }

    private static <T> T withTenant(UUID tenantId, TenantOperation<T> tenantOperation) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantOperation::run);
    }

    private static void withTenant(UUID tenantId, Runnable runnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(runnable);
    }

    @FunctionalInterface
    private interface TenantOperation<T> {
        T run();
    }
}
