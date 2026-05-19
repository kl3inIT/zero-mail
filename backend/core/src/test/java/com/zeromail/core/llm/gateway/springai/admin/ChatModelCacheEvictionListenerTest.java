package com.zeromail.core.llm.gateway.springai.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.event.MasterKeyRotatedEvent;
import com.zeromail.core.chat.llm.springai.SpringAiChatModelFactory;
import org.junit.jupiter.api.Test;

class ChatModelCacheEvictionListenerTest {

    @Test
    void evicts_chat_models_and_resolver_cache_after_master_key_commit() {
        SpringAiChatModelFactory chatModelFactory = mock(SpringAiChatModelFactory.class);
        ProviderMasterKeyResolver providerMasterKeyResolver = mock(ProviderMasterKeyResolver.class);
        ChatModelCacheEvictionListener listener =
                new ChatModelCacheEvictionListener(chatModelFactory, providerMasterKeyResolver);

        listener.on(new MasterKeyRotatedEvent(LlmProvider.OPENAI, 5L));

        verify(chatModelFactory).evictByProvider(LlmProvider.OPENAI);
        verify(providerMasterKeyResolver).invalidate(LlmProvider.OPENAI);
    }
}
