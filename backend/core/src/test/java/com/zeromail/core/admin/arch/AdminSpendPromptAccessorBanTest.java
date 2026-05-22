package com.zeromail.core.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaCall;
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

/**
 * OPS-SPEND-02 + ARCH-11 gate. Forbids spend controllers and services from reading any
 * content-shaped accessor — anything matching {@code
 * (?i)get(Prompt|Completion|Request|Response|Body|Content).*} — on any class. The spend dashboard
 * aggregates {@code llm_call_audit} via SQL SUM/COUNT only and MUST NEVER touch prompt or
 * completion text, request body, response body, or any other payload-shaped field.
 *
 * <p>The complementary {@link AdminPathBodyBanTest} already forbids the spend projection package
 * from declaring fields with the same name pattern. This rule extends the ban to <em>callers</em>
 * of accessors that match the regex regardless of which class declares them — so an accidental
 * dependency on a future {@code LlmCallAuditEntity.getPrompt*} accessor would be caught at
 * compile/test time.
 *
 * <p>Scope: {@code com.zeromail.core.admin.spend..} + {@code
 * com.zeromail.api.controllers.admin.AdminSpendController} + {@code
 * com.zeromail.api.dto.admin.spend..}.
 */
class AdminSpendPromptAccessorBanTest {

    private static final Pattern FORBIDDEN_ACCESSOR =
            Pattern.compile("(?i)get(Prompt|Completion|Request|Response|Body|Content).*");

    private static final Pattern FORBIDDEN_FIELD_NAME =
            Pattern.compile(
                    "(?i)(prompt|completion|requestBody|responseBody|bodyText|contentText).*");

    @Test
    void spend_code_never_reads_prompt_or_completion_accessors() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(
                                "com.zeromail.core.admin.spend..",
                                "com.zeromail.api.controllers.admin..",
                                "com.zeromail.api.dto.admin.spend..")
                        .should(new ForbidContentShapedAccess())
                        .because(
                                "OPS-SPEND-02 + ARCH-11: spend code reads llm_call_audit metadata"
                                        + " only — prompt/completion/body accessors are banned"
                                        + " across all callers, not just same-package fields.")
                        .allowEmptyShould(true);
        rule.check(importProductionClasses());
    }

    private static final class ForbidContentShapedAccess extends ArchCondition<JavaClass> {

        private ForbidContentShapedAccess() {
            super(
                    "never call getPrompt*/getCompletion*/getRequest*/getResponse*/getBody*"
                            + "/getContent* on any class, and never declare a field whose name"
                            + " matches the same regex");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
            // Exempt the controller's HttpServletResponse-typed parameters; bare method *names*
            // on Spring/Servlet types are not the threat.
            // The rule scans only methods CALLED FROM the spend class, so transitive Servlet
            // calls inside Spring infrastructure don't count.
            for (JavaCall<?> methodCall : javaClass.getMethodCallsFromSelf()) {
                String methodName = methodCall.getName();
                if (FORBIDDEN_ACCESSOR.matcher(methodName).matches()) {
                    String ownerName = methodCall.getTargetOwner().getName();
                    // Carve-out: HttpServletRequest/HttpServletResponse are not LLM payload
                    // surfaces; their getter shape is unrelated to email content. Explicitly
                    // allow those plus Spring's WebRequest types that share the same surface.
                    if (ownerName.startsWith("jakarta.servlet")
                            || ownerName.startsWith("org.springframework.web")
                            || ownerName.startsWith("org.springframework.http")) {
                        continue;
                    }
                    conditionEvents.add(
                            SimpleConditionEvent.violated(
                                    methodCall,
                                    "Forbidden content-shaped accessor call "
                                            + ownerName
                                            + "#"
                                            + methodName
                                            + " at "
                                            + methodCall.getSourceCodeLocation()));
                }
            }
            for (JavaField field : javaClass.getAllFields()) {
                if (FORBIDDEN_FIELD_NAME.matcher(field.getName()).matches()) {
                    conditionEvents.add(
                            SimpleConditionEvent.violated(
                                    field,
                                    "Forbidden content-shaped field declaration "
                                            + field.getFullName()));
                }
            }
        }
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
