package com.zeromail.core.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AdminPathBodyBanTest {

    private static final Pattern CONTENT_FIELD_NAME =
            Pattern.compile("(?i).*(body|bodyHtml|snippet|payload|prompt|completion|content).*");
    private static final Pattern CONTENT_GETTER_NAME =
            Pattern.compile("(?i)get(Body|BodyHtml|Snippet|Payload|Prompt|Completion|Content).*");
    private static final String ADMIN_REQUEST_BODY_ANNOTATION =
            "com.zeromail.api.security.AdminRequestBody";

    @Test
    void admin_paths_never_expose_or_pull_email_content_shapes() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(
                                "..controllers.admin..",
                                "..core.admin..projection..",
                                "..api.dto.admin..")
                        .should(new AdminContentShapeCondition())
                        .because(
                                "admin surfaces may inspect metadata and audit facts, not raw mailbox bodies or LLM payloads")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static final class AdminContentShapeCondition extends ArchCondition<JavaClass> {

        private AdminContentShapeCondition() {
            super("avoid fields and getters that expose body, prompt, completion, or payload data");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
            if (javaClass.isAnnotatedWith(ADMIN_REQUEST_BODY_ANNOTATION)) {
                return;
            }
            for (JavaField field : javaClass.getAllFields()) {
                if (CONTENT_FIELD_NAME.matcher(field.getName()).matches()) {
                    conditionEvents.add(
                            SimpleConditionEvent.violated(
                                    field,
                                    "Forbidden admin content-shaped field " + field.getFullName()));
                }
            }

            javaClass
                    .getMethodCallsFromSelf()
                    .forEach(
                            methodCall -> {
                                if (CONTENT_GETTER_NAME.matcher(methodCall.getName()).matches()) {
                                    conditionEvents.add(
                                            SimpleConditionEvent.violated(
                                                    methodCall,
                                                    "Forbidden admin content getter call at "
                                                            + methodCall.getSourceCodeLocation()));
                                }
                            });
        }
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
