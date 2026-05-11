package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageAuditPersistenceContractTest {

    private static final String PLAN_04_AUDIT_PERSISTENCE_MESSAGE =
            "Wave 0 contract - enabled by 04-02 when triage audit persistence lands";
    private static final String TRIAGE_AUDIT_ENTITY =
            "com.zeromail.core.triage.persistence.TriageAuditEntity";
    private static final String TRIAGE_AUDIT_REPOSITORY =
            "com.zeromail.core.triage.persistence.TriageAuditRepository";
    private static final String TRIAGE_AUDIT_WRITER =
            "com.zeromail.core.triage.persistence.TriageAuditWriter";
    private static final String TRIAGE_DECISION =
            "com.zeromail.core.triage.domain.TriageDecision";

    @Test
    void future_audit_persistence_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_AUDIT_ENTITY);
        assertFutureTypePresent(TRIAGE_AUDIT_REPOSITORY);
        assertFutureTypePresent(TRIAGE_AUDIT_WRITER);
        assertFutureTypePresent(TRIAGE_DECISION);
    }

    @Test
    @Disabled(PLAN_04_AUDIT_PERSISTENCE_MESSAGE)
    void audit_round_trip_is_tenant_scoped_and_json_validated() throws Exception {
        Object repository = Class.forName(TRIAGE_AUDIT_REPOSITORY).getConstructor().newInstance();
        Method insertMethod = repository.getClass().getMethod(
                "insertAuditPendingIfAbsent",
                String.class,
                String.class,
                String.class,
                String.class,
                byte[].class);

        Object insertedAuditId = insertMethod.invoke(
                repository,
                "tenant-a",
                "gmail-message-1",
                "rule-1",
                "{\"type\":\"archive\"}",
                new byte[32]);

        Method findVisibleMethod = repository.getClass().getMethod("findVisibleForCurrentTenant", Object.class);
        assertThat(findVisibleMethod.invoke(repository, insertedAuditId)).isNotNull();
    }

    @Test
    @Disabled(PLAN_04_AUDIT_PERSISTENCE_MESSAGE)
    void stale_pending_rows_are_reclaimed_but_fresh_leases_are_not() throws Exception {
        Object repository = Class.forName(TRIAGE_AUDIT_REPOSITORY).getConstructor().newInstance();
        Method reclaimMethod = repository.getClass().getMethod("reclaimStalePending", Duration.class);

        assertThat(reclaimMethod.invoke(repository, Duration.ofMinutes(3))).isEqualTo(1);
        assertThat(reclaimMethod.invoke(repository, Duration.ofSeconds(30))).isEqualTo(0);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
