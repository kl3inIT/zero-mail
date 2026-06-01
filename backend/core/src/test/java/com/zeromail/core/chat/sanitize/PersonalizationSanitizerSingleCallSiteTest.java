package com.zeromail.core.chat.sanitize;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceService;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PersonalizationSanitizerSingleCallSiteTest {

    @Test
    void sanitizerCallersAreLimitedToPromptRenderingAndVoiceSettings() {
        JavaClasses importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");

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
}
