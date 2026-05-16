package com.zeromail.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TriageAuditRepositoryBoundaryArchTest {

    static final Set<String> ALLOWED_TRIAGE_AUDIT_MUTATION_METHODS =
            Set.of(
                    "insertAuditPendingIfAbsent",
                    "insertAuditTerminalIfAbsent",
                    "reclaimStalePending",
                    "markApplied",
                    "markFailed",
                    "markRevertPending",
                    "markReverted");

    private static final String TRIAGE_AUDIT_REPOSITORY =
            "com.zeromail.core.triage.persistence.TriageAuditRepository";
    private static final Pattern AD_HOC_MUTATION_NAME = Pattern.compile("(?i).*(delete|update).*");

    @Test
    void triage_audit_repository_exposes_only_narrow_whitelisted_mutations() throws Exception {
        Class<?> repositoryClass = Class.forName(TRIAGE_AUDIT_REPOSITORY);

        Set<String> adHocMutationMethods =
                Arrays.stream(repositoryClass.getDeclaredMethods())
                        .map(Method::getName)
                        .filter(methodName -> AD_HOC_MUTATION_NAME.matcher(methodName).matches())
                        .filter(
                                methodName ->
                                        !ALLOWED_TRIAGE_AUDIT_MUTATION_METHODS.contains(methodName))
                        .collect(java.util.stream.Collectors.toSet());

        assertThat(adHocMutationMethods)
                .as(
                        "triage_audit is insert-only except the explicit PENDING/APPLIED/FAILED/REVERT_PENDING/REVERTED transitions")
                .isEmpty();
    }

    @Test
    void whitelist_names_are_visible_to_future_triage_audit_repository_plans() {
        assertThat(ALLOWED_TRIAGE_AUDIT_MUTATION_METHODS)
                .containsExactlyInAnyOrder(
                        "insertAuditPendingIfAbsent",
                        "insertAuditTerminalIfAbsent",
                        "reclaimStalePending",
                        "markApplied",
                        "markFailed",
                        "markRevertPending",
                        "markReverted");
        assertThat(TRIAGE_AUDIT_REPOSITORY)
                .isEqualTo("com.zeromail.core.triage.persistence.TriageAuditRepository");
    }
}
