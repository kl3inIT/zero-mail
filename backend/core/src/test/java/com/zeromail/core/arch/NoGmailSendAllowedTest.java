package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class NoGmailSendAllowedTest {

    private static final String GMAIL_MESSAGES_OWNER = "Gmail.Users.Messages";
    private static final String GMAIL_DRAFTS_OWNER = "Gmail.Users.Drafts";
    private static final String ALLOWED_SEND_CALL_SITE =
            "com.zeromail.core.outbound.usecases.AllowedSendCallSite";
    private static final String OUTBOUND_GATEWAY_PACKAGE = "com.zeromail.core.outbound";

    @ArchTest
    static final ArchRule no_code_calls_gmail_send_apis =
            noClasses()
                    .should(
                            new ArchCondition<JavaClass>(
                                    "call Gmail.Users.Messages.send or Gmail.Users.Drafts.send") {
                                @Override
                                public void check(
                                        JavaClass javaClass, ConditionEvents conditionEvents) {
                                    if (isAllowedOutboundGatewaySendOwner(javaClass)) {
                                        return;
                                    }
                                    javaClass
                                            .getMethodCallsFromSelf()
                                            .forEach(
                                                    methodCall -> {
                                                        String targetOwnerName =
                                                                methodCall
                                                                        .getTargetOwner()
                                                                        .getName()
                                                                        .replace('$', '.');
                                                        if (!methodCall.getName().equals("send")) {
                                                            return;
                                                        }
                                                        if (!targetOwnerName.endsWith(
                                                                        GMAIL_MESSAGES_OWNER)
                                                                && !targetOwnerName.endsWith(
                                                                        GMAIL_DRAFTS_OWNER)) {
                                                            return;
                                                        }
                                                        conditionEvents.add(
                                                                SimpleConditionEvent.violated(
                                                                        methodCall,
                                                                        "Forbidden Gmail send call at "
                                                                                + methodCall
                                                                                        .getSourceCodeLocation()));
                                                    });
                                }
                            })
                    .because(
                            "Phase 08.1 allows outbound automation only behind the shared outbound gateway boundary.")
                    .allowEmptyShould(false);

    private static boolean isAllowedOutboundGatewaySendOwner(JavaClass javaClass) {
        String className = javaClass.getName();
        return javaClass.isAnnotatedWith(ALLOWED_SEND_CALL_SITE)
                && className.startsWith(OUTBOUND_GATEWAY_PACKAGE + ".");
    }
}
