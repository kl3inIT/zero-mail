package com.zeromail.api.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Boundary: tenant-only Gmail client lookup is legacy-only during mailbox migration. */
class GmailClientLookupBoundaryTest {

    private static final Set<String> ALLOWED_TENANT_LOOKUP_CALLERS =
            new TreeSet<>(
                    List.of(
                            "com.zeromail.api.chat.AssistantPendingActionReconciler",
                            "com.zeromail.core.chat.usecases.settings.GmailSentMessagesReader",
                            "com.zeromail.core.chat.usecases.tools.GetMessageToolHandler",
                            "com.zeromail.core.chat.usecases.tools.GetThreadToolHandler",
                            "com.zeromail.core.chat.usecases.tools.ListLabelsToolHandler",
                            "com.zeromail.core.chat.usecases.tools.SearchInboxToolHandler"));

    private static final String GMAIL_CLIENT_FACTORY_OWNER =
            "com.zeromail.core.gmail.gateway.GmailApiClientFactory";

    @Test
    void only_allowed_legacy_callers_use_tenant_gmail_client_lookup_across_api_and_core() {
        JavaClasses productionClasses = importProductionClasses();

        tenantLookupRule().check(productionClasses);

        assertThat(actualTenantLookupCallers(productionClasses))
                .as("allow-list must match live production buildClientForTenant callers")
                .containsExactlyElementsOf(ALLOWED_TENANT_LOOKUP_CALLERS);
    }

    private static ArchRule tenantLookupRule() {
        return classes()
                .that()
                .resideInAPackage("com.zeromail..")
                .should(
                        new ArchCondition<JavaClass>(
                                "call GmailApiClientFactory.buildClientForTenant only from "
                                        + ALLOWED_TENANT_LOOKUP_CALLERS) {
                            @Override
                            public void check(
                                    JavaClass javaClass, ConditionEvents conditionEvents) {
                                if (ALLOWED_TENANT_LOOKUP_CALLERS.contains(javaClass.getName())) {
                                    return;
                                }
                                javaClass
                                        .getMethodCallsFromSelf()
                                        .forEach(
                                                methodCall -> {
                                                    if (!isTenantLookupCall(
                                                            methodCall.getTargetOwner().getName(),
                                                            methodCall.getName())) {
                                                        return;
                                                    }
                                                    conditionEvents.add(
                                                            SimpleConditionEvent.violated(
                                                                    methodCall,
                                                                    "Only "
                                                                            + ALLOWED_TENANT_LOOKUP_CALLERS
                                                                            + " may call GmailApiClientFactory.buildClientForTenant; found "
                                                                            + methodCall
                                                                                    .getSourceCodeLocation()));
                                                });
                            }
                        })
                .because(
                        "AUD-04/D-13: tenant-only Gmail lookup is forbidden in mailbox-scoped"
                                + " flows; only the explicit legacy migration allow-list may use it")
                .allowEmptyShould(false);
    }

    private static Set<String> actualTenantLookupCallers(JavaClasses productionClasses) {
        Set<String> callerNames = new TreeSet<>();
        productionClasses.forEach(
                javaClass -> {
                    boolean callsTenantLookup =
                            javaClass.getMethodCallsFromSelf().stream()
                                    .anyMatch(
                                            methodCall ->
                                                    isTenantLookupCall(
                                                            methodCall.getTargetOwner().getName(),
                                                            methodCall.getName()));
                    if (callsTenantLookup) {
                        callerNames.add(javaClass.getName());
                    }
                });
        return callerNames;
    }

    private static boolean isTenantLookupCall(String targetOwnerName, String methodName) {
        String normalizedOwnerName = targetOwnerName.replace('$', '.');
        return normalizedOwnerName.endsWith(GMAIL_CLIENT_FACTORY_OWNER)
                && methodName.equals("buildClientForTenant");
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
