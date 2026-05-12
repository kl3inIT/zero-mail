package com.zeromail.core.draft;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

class DraftPathArchUnitTest {

    @Test
    void draft_package_exists_as_contract_anchor() {
        assertThat(
                        importProductionClasses().stream()
                                .anyMatch(
                                        javaClass ->
                                                javaClass
                                                        .getPackageName()
                                                        .startsWith("com.zeromail.core.draft")))
                .as("RED until Plan 03 creates the core.draft production package")
                .isTrue();
    }

    @Test
    void draft_and_triage_paths_never_send_or_update_gmail_drafts() {
        noClasses()
                .that()
                .resideInAnyPackage("..core.draft..", "..core.triage..")
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
                                                    String ownerName =
                                                            methodCall
                                                                    .getTargetOwner()
                                                                    .getName()
                                                                    .replace('$', '.');
                                                    String methodName = methodCall.getName();
                                                    boolean messagesSend =
                                                            ownerName.endsWith(
                                                                            "Gmail.Users.Messages")
                                                                    && methodName.equals("send");
                                                    boolean draftsSendOrUpdate =
                                                            ownerName.endsWith("Gmail.Users.Drafts")
                                                                    && (methodName.equals("send")
                                                                            || methodName.equals(
                                                                                    "update"));
                                                    if (messagesSend || draftsSendOrUpdate) {
                                                        conditionEvents.add(
                                                                SimpleConditionEvent.violated(
                                                                        methodCall,
                                                                        "Forbidden Gmail send/update call at "
                                                                                + methodCall
                                                                                        .getSourceCodeLocation()));
                                                    }
                                                });
                            }
                        })
                .because("DRFT-04: Zero Mail may save drafts, but review/edit/send stay in Gmail.")
                .allowEmptyShould(true)
                .check(importProductionClasses());
    }

    @Test
    void spring_ai_never_leaks_into_draft_or_thread_packages() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..core.draft..", "..core.thread..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("org.springframework.ai..")
                        .because("Draft and thread code must use the pure-Java LlmGateway seam.")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    @Test
    void jakarta_mail_imports_stay_out_of_thread_package() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..core.thread..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("jakarta.mail..")
                        .because(
                                "Thread classification is metadata-only and must not own MIME building.")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static com.tngtech.archunit.core.domain.JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
