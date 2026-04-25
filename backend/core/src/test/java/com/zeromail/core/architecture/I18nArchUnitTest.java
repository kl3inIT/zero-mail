package com.zeromail.core.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Wave 0 RED stub — Plan 08 (architecture safety net) turns this GREEN.
 *
 * Asserts two i18n-specific architectural invariants:
 *
 *  1. {@code noMessageSourceInWebLayer}: classes under {@code com.zeromail.api..} must
 *     not depend on {@code org.springframework.context.MessageSource}. The server never
 *     constructs localized user-facing prose — that is the frontend's job. Threat #2
 *     in the planning_context security_threat_model.
 *
 *  2. {@code noFieldErrorVMShape}: controller-advice classes must not declare a record
 *     {@code FieldErrorVM} with a {@code message} String field. CONTEXT.md decision
 *     D-C2 locks the field-error shape to {@code { field, code, params }} — server-built
 *     {@code message} would re-introduce localized prose into the response.
 *
 * Implemented as a class-level @Disabled stub so the suite stays green; Plan 08 wires
 * the actual ArchUnit @AnalyzeClasses and rule definitions.
 */
@Disabled("Wave 0 RED stub — implemented by Plan 08")
class I18nArchUnitTest {

    @Test
    void noMessageSourceInWebLayer() {
        // Plan 08:
        //   noClasses().that().resideInAPackage("com.zeromail.api..")
        //       .should().dependOnClassesThat()
        //       .haveFullyQualifiedName("org.springframework.context.MessageSource")
        //       .check(importedClasses);
    }

    @Test
    void noFieldErrorVMShape() {
        // Plan 08: scan controller-advice classes for any nested record/class named
        // FieldErrorVM that declares a String message field. CONTEXT.md D-C2 locks the
        // shape to (field, code, params) — server-built message strings are forbidden.
    }
}
