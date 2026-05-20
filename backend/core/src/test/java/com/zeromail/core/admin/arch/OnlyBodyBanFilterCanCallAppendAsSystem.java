package com.zeromail.core.admin.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OnlyBodyBanFilterCanCallAppendAsSystem {

    private static final String AUDIT_WRITER =
            "com.zeromail.core.admin.audit.usecases.AdminAuditWriter";
    private static final Set<String> ALLOWED_CALLERS =
            Set.of(
                    "com.zeromail.api.security.AdminResponseBodyBanFilter",
                    "com.zeromail.worker.admin.AdminAuditChainVerifyJob");

    @Test
    void append_as_system_is_reserved_for_system_failsafes() {
        boolean allCallersAllowed =
                importProductionClasses().stream()
                        .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                        .filter(
                                methodCall ->
                                        methodCall.getTargetOwner().getName().equals(AUDIT_WRITER))
                        .filter(methodCall -> methodCall.getName().equals("appendAsSystem"))
                        .allMatch(
                                methodCall ->
                                        ALLOWED_CALLERS.contains(
                                                methodCall.getOriginOwner().getName()));

        assertThat(allCallersAllowed).isTrue();
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
