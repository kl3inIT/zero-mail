package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class ChatResponseToStringSafetyTest {

    @ArchTest
    static final ArchRule chat_response_to_string_banned_in_production =
            noClasses()
                    .that()
                    .resideInAPackage("com.zeromail..")
                    .should()
                    .callMethod(ChatResponse.class, "toString")
                    .because(
                            "LLM-09: ChatResponse.toString() may serialize prompt/completion content; "
                                    + "production code MUST extract metadata explicitly.");

    @ArchTest
    static final ArchRule assistant_message_to_string_banned_in_production =
            noClasses()
                    .that()
                    .resideInAPackage("com.zeromail..")
                    .should()
                    .callMethod(AssistantMessage.class, "toString")
                    .because(
                            "LLM-09: AssistantMessage.toString() may serialize model output; "
                                    + "production code MUST extract content and metadata explicitly.");
}
