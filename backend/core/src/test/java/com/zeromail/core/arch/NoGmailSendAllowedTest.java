package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.zeromail.core.rules.domain.RuleActionType;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class NoGmailSendAllowedTest {

    private static final String GMAIL_MESSAGES_OWNER = "Gmail.Users.Messages";
    private static final String GMAIL_DRAFTS_OWNER = "Gmail.Users.Drafts";

    @ArchTest
    static final ArchRule no_code_calls_gmail_send_apis =
            noClasses()
                    .should(
                            new ArchCondition<JavaClass>(
                                    "call Gmail.Users.Messages.send or Gmail.Users.Drafts.send") {
                                @Override
                                public void check(
                                        JavaClass javaClass, ConditionEvents conditionEvents) {
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
                            "TRG-03: Zero Mail v1 may label, archive, or save drafts, but must never send mail.")
                    .allowEmptyShould(true);

    @Test
    void rule_action_type_send_does_not_exist() {
        assertThatThrownBy(() -> RuleActionType.valueOf("SEND"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
