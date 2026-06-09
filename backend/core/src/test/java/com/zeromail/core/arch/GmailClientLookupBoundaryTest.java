package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

/** Boundary: tenant-only Gmail client lookup is legacy-only during mailbox migration. */
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class GmailClientLookupBoundaryTest {

    static final List<String> ALLOWED_TENANT_LOOKUP_CALLERS =
            List.of(
                    "com.zeromail.api.chat.AssistantPendingActionReconciler",
                    "com.zeromail.core.chat.usecases.settings.GmailSentMessagesReader",
                    "com.zeromail.core.chat.usecases.tools.GetMessageToolHandler",
                    "com.zeromail.core.chat.usecases.tools.GetThreadToolHandler",
                    "com.zeromail.core.chat.usecases.tools.ListLabelsToolHandler",
                    "com.zeromail.core.chat.usecases.tools.SearchInboxToolHandler",
                    "com.zeromail.core.draft.usecases.DraftReplySourceLoader",
                    "com.zeromail.core.draft.usecases.ToneContextBuilder",
                    "com.zeromail.core.gmail.usecases.GmailPreviewReadService",
                    "com.zeromail.core.outbound.usecases.ForwardMessageAssembler",
                    "com.zeromail.core.outbound.usecases.GmailOutboundSendGateway",
                    "com.zeromail.core.triage.usecases.TriageGmailWriter");

    private static final String GMAIL_CLIENT_FACTORY_OWNER =
            "com.zeromail.core.gmail.gateway.GmailApiClientFactory";

    @ArchTest
    static final ArchRule only_allowed_legacy_callers_use_tenant_gmail_client_lookup =
            classes()
                    .that()
                    .resideInAPackage("com.zeromail..")
                    .should(
                            new ArchCondition<JavaClass>(
                                    "call GmailApiClientFactory.buildClientForTenant only from "
                                            + ALLOWED_TENANT_LOOKUP_CALLERS) {
                                @Override
                                public void check(
                                        JavaClass javaClass, ConditionEvents conditionEvents) {
                                    if (ALLOWED_TENANT_LOOKUP_CALLERS.contains(
                                            javaClass.getName())) {
                                        return;
                                    }
                                    javaClass
                                            .getMethodCallsFromSelf()
                                            .forEach(
                                                    methodCall -> {
                                                        if (!isTenantLookupCall(
                                                                methodCall
                                                                        .getTargetOwner()
                                                                        .getName(),
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

    private static boolean isTenantLookupCall(String targetOwnerName, String methodName) {
        String normalizedOwnerName = targetOwnerName.replace('$', '.');
        return normalizedOwnerName.endsWith(GMAIL_CLIENT_FACTORY_OWNER)
                && methodName.equals("buildClientForTenant");
    }
}
