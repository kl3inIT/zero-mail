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

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class TriageGmailWriteBoundaryTest {

    private static final String TRIAGE_GMAIL_WRITER =
            "com.zeromail.core.triage.usecases.TriageGmailWriter";
    private static final String GMAIL_MESSAGES_OWNER = "Gmail.Users.Messages";
    private static final String GMAIL_DRAFTS_OWNER = "Gmail.Users.Drafts";

    @ArchTest
    static final ArchRule only_triage_gmail_writer_calls_gmail_write_apis =
            classes()
                    .that()
                    .resideInAPackage("..core.triage..")
                    .should(
                            new ArchCondition<JavaClass>(
                                    "call Gmail write APIs only from " + TRIAGE_GMAIL_WRITER) {
                                @Override
                                public void check(
                                        JavaClass javaClass, ConditionEvents conditionEvents) {
                                    if (javaClass.getName().equals(TRIAGE_GMAIL_WRITER)) {
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
                                                                                + TRIAGE_GMAIL_WRITER
                                                                                + " may call Gmail write APIs; found "
                                                                                + methodCall
                                                                                        .getSourceCodeLocation()));
                                                    });
                                }
                            })
                    .because(
                            "TRG-02: triage Gmail writes are centralized behind the audited writer.")
                    .allowEmptyShould(true);

    private static boolean isGmailWriteCall(String targetOwnerName, String methodName) {
        String normalizedOwnerName = targetOwnerName.replace('$', '.');
        boolean messageModify =
                normalizedOwnerName.endsWith(GMAIL_MESSAGES_OWNER) && methodName.equals("modify");
        boolean draftCreateOrDelete =
                normalizedOwnerName.endsWith(GMAIL_DRAFTS_OWNER)
                        && (methodName.equals("create") || methodName.equals("delete"));
        return messageModify || draftCreateOrDelete;
    }
}
