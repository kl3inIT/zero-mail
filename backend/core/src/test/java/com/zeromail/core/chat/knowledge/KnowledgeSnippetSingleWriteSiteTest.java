package com.zeromail.core.chat.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.zeromail.core.chat.persistence.AssistantKnowledgeMemoryJpaRepository;
import com.zeromail.core.chat.usecases.AssistantKnowledgeService;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KnowledgeSnippetSingleWriteSiteTest {

    @Test
    void onlyAssistantKnowledgeServiceWritesKnowledgeRepositoryRows() {
        JavaClasses importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");

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
}
