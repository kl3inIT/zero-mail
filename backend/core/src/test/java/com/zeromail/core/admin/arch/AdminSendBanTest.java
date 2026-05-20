package com.zeromail.core.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

class AdminSendBanTest {

    @Test
    void admin_paths_never_call_gmail_send_or_draft_send_update() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..controllers.admin..", "..core.admin..")
                        .should(
                                new ArchCondition<JavaClass>(
                                        "call Gmail.Users.Messages.send, Gmail.Users.Drafts.send, or Gmail.Users.Drafts.update") {
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
                                                            String targetMethodName =
                                                                    methodCall.getName();
                                                            boolean messagesSend =
                                                                    targetOwnerName.endsWith(
                                                                                    "Gmail.Users.Messages")
                                                                            && targetMethodName
                                                                                    .equals("send");
                                                            boolean draftsSendOrUpdate =
                                                                    targetOwnerName.endsWith(
                                                                                    "Gmail.Users.Drafts")
                                                                            && (targetMethodName
                                                                                            .equals(
                                                                                                    "send")
                                                                                    || targetMethodName
                                                                                            .equals(
                                                                                                    "update"));
                                                            if (messagesSend
                                                                    || draftsSendOrUpdate) {
                                                                conditionEvents.add(
                                                                        SimpleConditionEvent
                                                                                .violated(
                                                                                        methodCall,
                                                                                        "Forbidden Gmail send/update call at "
                                                                                                + methodCall
                                                                                                        .getSourceCodeLocation()));
                                                            }
                                                        });
                                    }
                                })
                        .because("admin tooling must never send Gmail mail or update Gmail drafts")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
