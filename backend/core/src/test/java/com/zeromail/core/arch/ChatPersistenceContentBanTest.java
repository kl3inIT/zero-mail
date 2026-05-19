package com.zeromail.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ChatPersistenceContentBanTest {

    private static final Path CHAT_SOURCE_ROOT =
            Path.of("backend/core/src/main/java/com/zeromail/core/chat");
    private static final Path CHAT_PERSISTENCE_ROOT = CHAT_SOURCE_ROOT.resolve("persistence");
    private static final Path TOOL_OUTPUT_SANITIZER =
            CHAT_SOURCE_ROOT.resolve("sanitize/ToolOutputSanitizer.java");
    private static final Pattern BODY_FIELD_PATTERN =
            Pattern.compile(
                    "(?i)\\b(emailBody|messageBody|bodyHtml|bodyText|htmlBody|textBody|body)\\b");
    private static final List<String> EMAIL_READ_TOOL_TYPES =
            List.of("tool-getMessage", "tool-searchInbox", "tool-getThread", "tool-listLabels");
    private static final List<String> DRAFT_TOOL_TYPES =
            List.of("tool-sendEmail", "tool-replyEmail", "tool-forwardEmail", "tool-saveDraft");

    @Test
    void chat_persistence_sources_do_not_declare_body_shaped_fields() throws IOException {
        if (!Files.exists(CHAT_PERSISTENCE_ROOT)) {
            return;
        }

        for (Path persistenceSource : javaSourcesUnder(CHAT_PERSISTENCE_ROOT)) {
            String sourceText = Files.readString(persistenceSource);

            assertThat(sourceText)
                    .as(
                            "chat_message.parts must route through ToolOutputSanitizer "
                                    + "(source-aware: strips body from email-read tool outputs, "
                                    + "preserves body in send/draft tool args per HIGH-3); "
                                    + "see ARCH-02 + CLAUDE.md Privacy Draft-body carve-out")
                    .doesNotContainPattern(BODY_FIELD_PATTERN);
        }
    }

    @Test
    void tool_output_sanitizer_declares_source_aware_body_policy_when_chat_exists()
            throws IOException {
        if (!Files.exists(CHAT_SOURCE_ROOT)) {
            return;
        }

        assertThat(TOOL_OUTPUT_SANITIZER)
                .as("ToolOutputSanitizer is the mandatory source-aware chat_message.parts gate")
                .exists();

        String sanitizerSource = Files.readString(TOOL_OUTPUT_SANITIZER);

        assertThat(sanitizerSource)
                .as("Sanitizer must know email-read tool outputs whose body fields are banned")
                .contains(EMAIL_READ_TOOL_TYPES.toArray(String[]::new));
        assertThat(sanitizerSource)
                .as("Sanitizer must preserve user-authored draft args for send/draft tools")
                .contains(DRAFT_TOOL_TYPES.toArray(String[]::new));
        assertThat(sanitizerSource)
                .as("Sanitizer must actively inspect body-shaped field names")
                .containsPattern(BODY_FIELD_PATTERN);
        assertThat(sanitizerSource.toLowerCase(Locale.ROOT))
                .as("Sanitizer must expose a sanitize entry point")
                .contains("sanitize");
    }

    @Test
    void chat_persistence_does_not_depend_on_llm_or_gmail_content_surfaces() {
        JavaClasses importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");

        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("..core.chat.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..core.llm..", "..core.gmail..")
                .because(
                        "chat_message.parts persistence may store sanitized chat envelopes only, "
                                + "not raw Gmail or LLM content surfaces")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    private static List<Path> javaSourcesUnder(Path sourceRoot) throws IOException {
        try (var sourceStream = Files.walk(sourceRoot)) {
            return sourceStream.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
