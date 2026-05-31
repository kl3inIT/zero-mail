package com.zeromail.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.support.ApiPostgresTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class SpringAiObservationDisabledTest extends ApiPostgresTestBase {

    @Autowired private SpringAiObservationProperties springAiObservationProperties;
    @Autowired private Environment environment;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void springAiPromptAndCompletionObservationCaptureIsDisabled() throws Exception {
        assertThat(springAiObservationProperties.logPrompt()).isFalse();
        assertThat(springAiObservationProperties.logCompletion()).isFalse();
        assertThat(environment.containsProperty(SpringAiObservationProperties.LOG_PROMPT_KEY))
                .isTrue();
        assertThat(environment.containsProperty(SpringAiObservationProperties.LOG_COMPLETION_KEY))
                .isTrue();
        assertThat(environment.getProperty(SpringAiObservationProperties.LOG_PROMPT_KEY))
                .isEqualTo("false");
        assertThat(environment.getProperty(SpringAiObservationProperties.LOG_COMPLETION_KEY))
                .isEqualTo("false");

        assertThat(observationHandlerBeanClassNames())
                .noneMatch(
                        className -> className.endsWith("ChatModelPromptContentObservationHandler"))
                .noneMatch(className -> className.endsWith("ChatModelCompletionObservationHandler"))
                .noneMatch(
                        className ->
                                className.endsWith("ChatClientPromptContentObservationHandler"))
                .noneMatch(
                        className -> className.endsWith("ChatClientCompletionObservationHandler"));

        // The prompt/completion suppression keys are centralized in backend/core's
        // zero-mail-shared.yml, imported by both the api and worker runtimes. Assert the
        // shared file declares them false so every runtime inherits the privacy invariant
        // statically, not only via runtime property injection.
        Properties sharedProperties = yamlProperties(sharedPrivacyYaml());
        assertThat(sharedProperties.getProperty(SpringAiObservationProperties.LOG_PROMPT_KEY))
                .isEqualTo("false");
        assertThat(sharedProperties.getProperty(SpringAiObservationProperties.LOG_COMPLETION_KEY))
                .isEqualTo("false");
    }

    private static Properties yamlProperties(Path yamlPath) {
        YamlPropertiesFactoryBean yamlPropertiesFactoryBean = new YamlPropertiesFactoryBean();
        yamlPropertiesFactoryBean.setResources(new FileSystemResource(yamlPath));
        Properties properties = yamlPropertiesFactoryBean.getObject();
        return properties == null ? new Properties() : properties;
    }

    private java.util.List<String> observationHandlerBeanClassNames() {
        return Arrays.stream(applicationContext.getBeanDefinitionNames())
                .map(applicationContext::getType)
                .filter(java.util.Objects::nonNull)
                .map(Class::getName)
                .toList();
    }

    private static Path sharedPrivacyYaml() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("backend/core/src/main/resources/zero-mail-shared.yml");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate zero-mail-shared.yml");
    }
}
