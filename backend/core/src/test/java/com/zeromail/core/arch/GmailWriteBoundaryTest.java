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

/**
 * Boundary: only an explicit allow-list of classes may invoke Gmail write APIs (label modify, draft
 * create/delete, message send). Phase 4 originally guarded {@code core.triage} only; Phase 8
 * (bulk-unsubscribe-campaign) extends the scope to all of {@code core}. Phase 08.1 introduced
 * {@link com.zeromail.core.outbound.usecases.GmailOutboundSendGateway} as the single shared owner
 * of {@code Gmail.users().messages().send()} — the {@code UnsubscribeMailtoSender} and chat {@code
 * AssistantSendExecutor} delegate through it rather than calling Gmail directly.
 *
 * <p>Auto-send of arbitrary user-composed mail is still forbidden — only the unsubscribe-mailto
 * code path and chat user-confirmed send path are routed through the gateway, and each call site
 * has its own input-validation invariants (see {@code UnsubscribeMailtoSenderRecipientGuardTest}
 * for the mailto path).
 */
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class GmailWriteBoundaryTest {

    static final List<String> ALLOWED_GMAIL_WRITERS =
            List.of(
                    "com.zeromail.core.triage.usecases.TriageGmailWriter",
                    "com.zeromail.core.outbound.usecases.GmailOutboundSendGateway");
    private static final String GMAIL_MESSAGES_OWNER = "Gmail.Users.Messages";
    private static final String GMAIL_DRAFTS_OWNER = "Gmail.Users.Drafts";

    @ArchTest
    static final ArchRule only_allowed_writers_call_gmail_write_apis =
            classes()
                    .that()
                    .resideInAPackage("..core..")
                    .should(
                            new ArchCondition<JavaClass>(
                                    "call Gmail write APIs only from " + ALLOWED_GMAIL_WRITERS) {
                                @Override
                                public void check(
                                        JavaClass javaClass, ConditionEvents conditionEvents) {
                                    if (ALLOWED_GMAIL_WRITERS.contains(javaClass.getName())) {
                                        return;
                                    }
                                    javaClass
                                            .getMethodCallsFromSelf()
                                            .forEach(
                                                    methodCall -> {
                                                        if (!isGmailWriteCall(
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
                                                                                + ALLOWED_GMAIL_WRITERS
                                                                                + " may call Gmail write APIs; found "
                                                                                + methodCall
                                                                                        .getSourceCodeLocation()));
                                                    });
                                }
                            })
                    .because(
                            "TRG-02 + UNS-08b: Gmail writes (label modify, draft create/delete,"
                                    + " message send) are centralized behind audited writers.")
                    .allowEmptyShould(true);

    private static boolean isGmailWriteCall(String targetOwnerName, String methodName) {
        String normalizedOwnerName = targetOwnerName.replace('$', '.');
        boolean messageModify =
                normalizedOwnerName.endsWith(GMAIL_MESSAGES_OWNER) && methodName.equals("modify");
        boolean messageSend =
                normalizedOwnerName.endsWith(GMAIL_MESSAGES_OWNER) && methodName.equals("send");
        boolean draftCreateOrDelete =
                normalizedOwnerName.endsWith(GMAIL_DRAFTS_OWNER)
                        && (methodName.equals("create") || methodName.equals("delete"));
        return messageModify || messageSend || draftCreateOrDelete;
    }
}
