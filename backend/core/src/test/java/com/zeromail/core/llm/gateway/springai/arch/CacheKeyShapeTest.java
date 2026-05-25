package com.zeromail.core.llm.gateway.springai.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.llm.springai.SpringAiChatModelFactory;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CacheKeyShapeTest {

    @Test
    void chat_model_cache_key_uses_provider_secret_version_not_kek_version() {
        Class<?> cacheKeyClass =
                Arrays.stream(SpringAiChatModelFactory.class.getDeclaredClasses())
                        .filter(
                                declaredClass ->
                                        declaredClass.getSimpleName().equals("ChatClientCacheKey"))
                        .findFirst()
                        .orElseThrow();

        Set<String> cacheKeyFieldNames =
                Arrays.stream(cacheKeyClass.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName)
                        .collect(java.util.stream.Collectors.toSet());
        assertThat(cacheKeyFieldNames)
                .contains("providerSecretVersion")
                .doesNotContain("kekVersion");
    }
}
