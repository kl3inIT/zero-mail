package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.worker.notification.config.DigestRendererConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

class DigestMessageSourceParityTest {

    @Test
    void vi_and_en_digest_bundles_have_matching_key_sets_and_resolve_loudly() throws IOException {
        Properties englishMessages = loadProperties("i18n/digest_en.properties");
        Properties vietnameseMessages = loadProperties("i18n/digest_vi.properties");
        assertThat(vietnameseMessages.stringPropertyNames())
                .containsExactlyInAnyOrderElementsOf(englishMessages.stringPropertyNames());

        MessageSource digestMessageSource = new DigestRendererConfig().digestMessageSource();
        Set<String> keys = englishMessages.stringPropertyNames();
        for (String key : keys) {
            assertThat(digestMessageSource.getMessage(key, sampleArguments(key), Locale.ENGLISH))
                    .isNotBlank()
                    .doesNotContain("??");
            assertThat(
                            digestMessageSource.getMessage(
                                    key, sampleArguments(key), Locale.forLanguageTag("vi")))
                    .isNotBlank()
                    .doesNotContain("??");
        }
    }

    private static Properties loadProperties(String classpathLocation) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(classpathLocation)) {
            assertThat(inputStream).as(classpathLocation).isNotNull();
            properties.load(new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static Object[] sampleArguments(String key) {
        if (key.equals("digest.subject")) {
            return new Object[] {47, "18m"};
        }
        if (key.equals("digest.footer.brand")) {
            return new Object[] {"zero-mail.test"};
        }
        return new Object[0];
    }
}
