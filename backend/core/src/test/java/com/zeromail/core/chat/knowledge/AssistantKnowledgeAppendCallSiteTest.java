package com.zeromail.core.chat.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.zeromail.core.chat.usecases.AssistantKnowledgeService;
import com.zeromail.core.chat.usecases.tools.WriteReversibleToolHandlers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AssistantKnowledgeAppendCallSiteTest {

    @Test
    void appendCallersAreLimitedToChatToolHandlerAndKnowledgeController() throws IOException {
        JavaClasses importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");

        Set<String> coreAppendCallers =
                importedClasses.stream()
                        .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                        .filter(
                                methodCall ->
                                        methodCall
                                                .getTargetOwner()
                                                .isEquivalentTo(AssistantKnowledgeService.class))
                        .filter(methodCall -> methodCall.getName().equals("append"))
                        .map(methodCall -> methodCall.getOriginOwner().getName())
                        .collect(Collectors.toSet());

        assertThat(coreAppendCallers).containsExactly(WriteReversibleToolHandlers.class.getName());
        assertThat(apiAppendCallerFiles())
                .containsExactly(
                        repositoryRoot()
                                .resolve(
                                        "backend/api/src/main/java/com/zeromail/api/controllers/settings/KnowledgeSnippetController.java"));
    }

    private static Set<Path> apiAppendCallerFiles() throws IOException {
        Path repositoryRoot = repositoryRoot();
        Path apiSourceRoot = repositoryRoot.resolve("backend/api/src/main/java");
        try (var sourceFiles = Files.walk(apiSourceRoot)) {
            return sourceFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(AssistantKnowledgeAppendCallSiteTest::containsAppendCall)
                    .collect(Collectors.toSet());
        }
    }

    private static Path repositoryRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        while (currentDirectory != null) {
            if (Files.exists(currentDirectory.resolve("settings.gradle.kts"))) {
                return currentDirectory;
            }
            currentDirectory = currentDirectory.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }

    private static boolean containsAppendCall(Path sourceFile) {
        try {
            return Files.readString(sourceFile).contains("assistantKnowledgeService.append(");
        } catch (IOException sourceReadFailure) {
            throw new IllegalStateException("Failed to read API source file", sourceReadFailure);
        }
    }
}
