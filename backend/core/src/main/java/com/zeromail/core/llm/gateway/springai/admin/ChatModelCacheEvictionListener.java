package com.zeromail.core.llm.gateway.springai.admin;

import com.zeromail.core.admin.cat.domain.event.CatalogChangedEvent;
import com.zeromail.core.admin.cat.usecases.CuratedCatalogQueryService;
import com.zeromail.core.admin.mkey.domain.event.MasterKeyRotatedEvent;
import com.zeromail.core.chat.llm.springai.SpringAiChatModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatModelCacheEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(ChatModelCacheEvictionListener.class);

    private final SpringAiChatModelFactory chatModelFactory;
    private final ProviderMasterKeyResolver providerMasterKeyResolver;
    private final CuratedCatalogQueryService curatedCatalogQueryService;

    public ChatModelCacheEvictionListener(
            SpringAiChatModelFactory chatModelFactory,
            ProviderMasterKeyResolver providerMasterKeyResolver,
            CuratedCatalogQueryService curatedCatalogQueryService) {
        this.chatModelFactory = chatModelFactory;
        this.providerMasterKeyResolver = providerMasterKeyResolver;
        this.curatedCatalogQueryService = curatedCatalogQueryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MasterKeyRotatedEvent event) {
        chatModelFactory.evictByProvider(event.provider());
        providerMasterKeyResolver.invalidate(event.provider());
        log.info(
                "event=chat_model_cache_evicted reason=master_key_rotated provider={}",
                event.provider());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CatalogChangedEvent event) {
        chatModelFactory.evictByModelIds(event.affectedModelIds());
        curatedCatalogQueryService.invalidateCache();
        log.info(
                "event=chat_model_cache_evicted reason=catalog_changed provider={} catalogVersion={}",
                event.provider(),
                event.newCatalogVersion());
    }
}
