package com.zeromail.core.chat.llm.springai;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.event.CatalogChangedEvent;
import com.zeromail.core.admin.cat.usecases.CuratedCatalogQueryService;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.event.MasterKeyRotatedEvent;
import com.zeromail.core.admin.mkey.usecases.ProviderMasterKeyResolver;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatModelCacheEvictionListenerTest {

    @Test
    void evicts_chat_models_and_resolver_cache_after_master_key_commit() {
        SpringAiChatModelFactory chatModelFactory = mock(SpringAiChatModelFactory.class);
        ProviderMasterKeyResolver providerMasterKeyResolver = mock(ProviderMasterKeyResolver.class);
        CuratedCatalogQueryService curatedCatalogQueryService =
                mock(CuratedCatalogQueryService.class);
        ChatModelCacheEvictionListener listener =
                new ChatModelCacheEvictionListener(
                        chatModelFactory, providerMasterKeyResolver, curatedCatalogQueryService);

        listener.on(new MasterKeyRotatedEvent(LlmProvider.OPENAI, 5L));

        verify(chatModelFactory).evictByProvider(LlmProvider.OPENAI);
        verify(providerMasterKeyResolver).invalidate(LlmProvider.OPENAI);
    }

    @Test
    void evicts_affected_models_and_catalog_cache_after_catalog_commit() {
        SpringAiChatModelFactory chatModelFactory = mock(SpringAiChatModelFactory.class);
        ProviderMasterKeyResolver providerMasterKeyResolver = mock(ProviderMasterKeyResolver.class);
        CuratedCatalogQueryService curatedCatalogQueryService =
                mock(CuratedCatalogQueryService.class);
        ChatModelCacheEvictionListener listener =
                new ChatModelCacheEvictionListener(
                        chatModelFactory, providerMasterKeyResolver, curatedCatalogQueryService);
        List<String> affectedModelIds = List.of("openai/gpt-test");

        listener.on(
                new CatalogChangedEvent(
                        LlmProvider.OPENAI,
                        affectedModelIds,
                        Set.of(Feature.CHAT),
                        Instant.parse("2026-05-20T00:00:00Z"),
                        12L));

        verify(chatModelFactory).evictByModelIds(affectedModelIds);
        verify(curatedCatalogQueryService).invalidateCache();
    }
}
