package com.zeromail.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.zeromail.core.chat.persistence.AssistantKnowledgeMemoryJpaRepository;
import com.zeromail.core.chat.sanitize.PersonalizationSanitizer;
import com.zeromail.core.chat.sanitize.XmlFencedPersonalizationRenderer;
import com.zeromail.core.chat.usecases.AssistantKnowledgeService;
import com.zeromail.core.chat.usecases.settings.GmailSentMessagesReader;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceService;
import com.zeromail.core.chat.usecases.tools.WriteReversibleToolHandlers;
import com.zeromail.core.llm.byok.UserByokKeyEntity;
import com.zeromail.core.llm.byok.UserByokKeyRepository;
import com.zeromail.core.llm.gateway.springai.ProviderConnectionTester;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class Phase9ArchitectureTest {

    @ArchTest
    static void personalization_sanitizer_callers_stay_limited(JavaClasses importedClasses) {
        Set<String> callerClassNames =
                importedClasses.stream()
                        .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                        .filter(
                                methodCall ->
                                        methodCall
                                                .getTargetOwner()
                                                .isEquivalentTo(PersonalizationSanitizer.class))
                        .filter(methodCall -> methodCall.getName().equals("sanitize"))
                        .map(methodCall -> methodCall.getOriginOwner().getName())
                        .collect(Collectors.toSet());

        assertThat(callerClassNames)
                .containsExactlyInAnyOrder(
                        XmlFencedPersonalizationRenderer.class.getName(),
                        SettingsVoiceService.class.getName());
    }

    @ArchTest
    static void knowledge_append_callers_stay_limited(JavaClasses importedClasses) {
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

    @ArchTest
    static void knowledge_repository_write_site_stays_single(JavaClasses importedClasses) {
        Set<String> repositoryWriteCallers =
                importedClasses.stream()
                        .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                        .filter(
                                methodCall ->
                                        methodCall
                                                .getTargetOwner()
                                                .isEquivalentTo(
                                                        AssistantKnowledgeMemoryJpaRepository
                                                                .class))
                        .filter(
                                methodCall ->
                                        methodCall.getName().equals("save")
                                                || methodCall.getName().equals("saveAndFlush"))
                        .map(methodCall -> methodCall.getOriginOwner().getName())
                        .collect(Collectors.toSet());

        assertThat(repositoryWriteCallers)
                .containsExactly(AssistantKnowledgeService.class.getName());
    }

    @ArchTest
    static void provider_connection_tester_bindings_stay_limited(JavaClasses importedClasses) {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackages(
                                "..core.admin.mkey.usecases..", "..core.llm.byok..")
                        .and()
                        .areNotAssignableTo(ProviderConnectionTester.class)
                        .should()
                        .callMethodWhere(providerConnectionTesterProbeCall())
                        .because(
                                "Only admin master-key tests and user BYOK tests may probe provider keys")
                        .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @ArchTest
    static void user_byok_key_persistence_stays_confined(JavaClasses importedClasses) {
        noClasses()
                .that()
                .resideOutsideOfPackages(
                        "..core.chat.byok..", "..core.llm.byok..", "..core.llm.gateway..")
                .and()
                .areNotAssignableTo(UserByokKeyEntity.class)
                .and()
                .areNotAssignableTo(UserByokKeyRepository.class)
                .should()
                .dependOnClassesThat()
                .areAssignableTo(UserByokKeyEntity.class)
                .because("user_byok_key entity access must stay inside BYOK routing boundaries")
                .allowEmptyShould(true)
                .check(importedClasses);

        noClasses()
                .that()
                .resideOutsideOfPackages(
                        "..core.chat.byok..", "..core.llm.byok..", "..core.llm.gateway..")
                .and()
                .areNotAssignableTo(UserByokKeyEntity.class)
                .and()
                .areNotAssignableTo(UserByokKeyRepository.class)
                .should()
                .dependOnClassesThat()
                .areAssignableTo(UserByokKeyRepository.class)
                .because("user_byok_key repository access must stay inside BYOK routing boundaries")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    @ArchTest
    static void settings_use_cases_do_not_reach_google_gmail_clients_directly(
            JavaClasses importedClasses) {
        noClasses()
                .that()
                .resideInAPackage("..core.chat.usecases.settings..")
                .and()
                .areNotAssignableTo(GmailSentMessagesReader.class)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.google.api.services.gmail..")
                .because("voice settings must read sent mail through GmailSentMessagesReader")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    private static DescribedPredicate<JavaMethodCall> providerConnectionTesterProbeCall() {
        return new DescribedPredicate<>("ProviderConnectionTester.probeConnection") {
            @Override
            public boolean test(JavaMethodCall methodCall) {
                return methodCall.getTarget().getName().equals("probeConnection")
                        && methodCall
                                .getTarget()
                                .getOwner()
                                .isAssignableTo(ProviderConnectionTester.class);
            }
        };
    }

    private static Set<Path> apiAppendCallerFiles() {
        Path repositoryRoot = repositoryRoot();
        Path apiSourceRoot = repositoryRoot.resolve("backend/api/src/main/java");
        try (Stream<Path> sourceFiles = Files.walk(apiSourceRoot)) {
            return sourceFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(Phase9ArchitectureTest::containsAppendCall)
                    .collect(Collectors.toSet());
        } catch (IOException sourceWalkFailure) {
            throw new IllegalStateException("Failed to scan API source files", sourceWalkFailure);
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
