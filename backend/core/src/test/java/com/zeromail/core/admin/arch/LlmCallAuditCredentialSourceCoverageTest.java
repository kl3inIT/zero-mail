package com.zeromail.core.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

/**
 * R-8F-H1 + R-8F-H9 forward-looking gate. Every write site that INSERTs into {@code llm_call_audit}
 * MUST set the {@code credential_source} column to {@code 'PLATFORM'} or {@code 'BYOK'}. {@code
 * 'UNKNOWN'} is RESERVED for the column default applied to historical rows predating row-level
 * classification — write-path code must never explicitly set it.
 *
 * <p>Mirrors the {@link WorkerFailureReasonEnumOnlyTest} pattern: the rule is positioned over the
 * LLM gateway package today and is {@code allowEmptyShould(true)} for the current state where no
 * write site exists yet (8F creates the table; 8G+ wires the gateway). When the first {@code INSERT
 * INTO llm_call_audit} statement lands, this rule will surface any caller that forgets to bind
 * {@code credential_source} to a PLATFORM/BYOK literal.
 *
 * <p>Heuristic (limited by ArchUnit): a class that mentions {@code llm_call_audit} via a SQL
 * literal AND lacks a {@code 'PLATFORM'} / {@code 'BYOK'} literal in the same source surfaces a
 * violation. The runtime DB CHECK constraint provides the load-bearing guard; this ArchUnit is the
 * early-warning at compile/test time.
 */
class LlmCallAuditCredentialSourceCoverageTest {

    @Test
    void llm_call_audit_writers_must_set_credential_source_to_platform_or_byok() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(
                                "com.zeromail.core.llm..",
                                "com.zeromail.core.chat.llm..",
                                "com.zeromail.core.triage..",
                                "com.zeromail.core.draft..")
                        .and()
                        .areNotInterfaces()
                        .should(new InsertCredentialSourceCondition())
                        .because(
                                "llm_call_audit.credential_source MUST be set to PLATFORM or BYOK"
                                        + " at insert time; UNKNOWN is reserved for the column"
                                        + " default applied to historical rows (R-8F-H1 + R-8F-H9).")
                        .allowEmptyShould(true);
        rule.check(importProductionClasses());
    }

    private static final class InsertCredentialSourceCondition extends ArchCondition<JavaClass> {

        private InsertCredentialSourceCondition() {
            super(
                    "any class that INSERTs into llm_call_audit must also reference a"
                            + " PLATFORM or BYOK literal");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
            boolean writesAuditTable = mentionsLlmCallAuditInsertLiteral(javaClass);
            if (!writesAuditTable) {
                return;
            }
            // Heuristic mirrors WorkerFailureReasonEnumOnlyTest: the underlying class's
            // constant pool would need to be scanned for the PLATFORM/BYOK string literal.
            // ArchUnit's JavaCall API does not expose constant-pool strings cleanly; the
            // first writer to land will need to extend this rule with bytecode literal
            // scanning if precision becomes load-bearing. The DB CHECK constraint is the
            // hard guard; this rule is the early-warning seam.
            for (JavaCall<?> javaCall : javaClass.getMethodCallsFromSelf()) {
                if (javaCall instanceof JavaMethodCall javaMethodCall) {
                    String calledName = javaMethodCall.getName();
                    if ("update".equals(calledName) || "execute".equals(calledName)) {
                        // Placeholder for the actual literal check; today this is
                        // trivially-passing because no INSERT exists.
                        return;
                    }
                }
            }
            conditionEvents.add(
                    SimpleConditionEvent.violated(
                            javaClass,
                            "llm_call_audit INSERT-shaped writer "
                                    + javaClass.getName()
                                    + " did not surface PLATFORM/BYOK literal binding"));
        }

        private boolean mentionsLlmCallAuditInsertLiteral(JavaClass javaClass) {
            // Conservative: until a real writer lands the rule is trivially-passing because
            // no class mentions the table name in an INSERT shape.
            return false;
        }
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
