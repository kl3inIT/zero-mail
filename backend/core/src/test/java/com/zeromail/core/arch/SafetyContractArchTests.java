package com.zeromail.core.arch;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
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
public class SafetyContractArchTests {

    private static final String SENSITIVE = "com.zeromail.core.privacy.Sensitive";
    private static final Set<String> DENY = Set.of(
            "body", "bodyText", "prompt", "completion", "rawContent", "refreshToken", "accessToken");
    private static final String DENY_REGEX = String.join("|", DENY);

    // allowEmptyShould(true): the deny-listed fields don't exist yet (they're introduced
    // in plan 04 as e.g. `Sensitive<String> refreshToken`). The rule's job is to fail the
    // build if any unwrapped variant is later added.
    @ArchTest
    static final ArchRule sensitive_names_wrapped =
            fields().that().haveNameMatching(DENY_REGEX)
                    .should().haveRawType(SENSITIVE)
                    .because("FND-03/04: deny-listed names must be Sensitive<T>")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_sensitive_in_logger =
            noClasses().should(new ArchCondition<JavaClass>("pass Sensitive to Logger") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.getMethodCallsFromSelf().forEach(call -> {
                        if (!call.getTargetOwner().isAssignableTo("org.slf4j.Logger")) {
                            return;
                        }
                        boolean passesSensitive = call.getTarget().getRawParameterTypes().stream()
                                .anyMatch(t -> t.getName().equals(SENSITIVE));
                        if (passesSensitive) {
                            events.add(SimpleConditionEvent.violated(call,
                                    "Logger call at " + call.getSourceCodeLocation()
                                            + " passes a Sensitive value"));
                        }
                    });
                }
            });
}
