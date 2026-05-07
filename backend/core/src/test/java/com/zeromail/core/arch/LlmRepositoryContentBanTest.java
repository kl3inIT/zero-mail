package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class LlmRepositoryContentBanTest {

    // Banned token documentation for the grep acceptance check:
    // prompt
    // completion
    // messageBody
    // apiKey
    // decryptedKey
    private static final Pattern BANNED_METHOD_NAME = Pattern.compile(
            "(?i).*(prompt|completion|messageBody|emailBody|rawHtml|apiKey|decryptedKey|plaintextKey).*");

    @ArchTest
    static final ArchRule repos_must_not_accept_prompt_or_completion_or_body_content_args = classes()
            .that().resideInAPackage("..persistence..")
            .and().haveSimpleNameEndingWith("Repository")
            .should(new ArchCondition<JavaClass>(
                    "not declare content-like String methods that can persist LLM data or keys") {
                @Override
                public void check(JavaClass repositoryClass, ConditionEvents events) {
                    for (JavaMethod repositoryMethod : repositoryClass.getMethods()) {
                        if (!BANNED_METHOD_NAME.matcher(repositoryMethod.getName()).matches()) {
                            continue;
                        }
                        boolean hasStringParameter = repositoryMethod.getRawParameterTypes().stream()
                                .anyMatch(parameterType -> parameterType.getName().equals("java.lang.String"));
                        if (hasStringParameter) {
                            events.add(SimpleConditionEvent.violated(
                                    repositoryMethod,
                                    "Repository " + repositoryClass.getName() + " method "
                                            + repositoryMethod.getName()
                                            + " has a String parameter and a content-like name; "
                                            + "LLM-09 forbids repositories from accepting prompt/"
                                            + "completion/body/key content. Move the logic up to a "
                                            + "service that does not persist."));
                        }
                    }
                }
            });
}
