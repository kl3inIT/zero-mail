package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.persistence.AssistantMemoryEntity;
import com.zeromail.core.chat.persistence.AssistantMemoryJpaRepository;
import com.zeromail.core.chat.usecases.ChatToolCatalog.SearchMemoriesArgs;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class SearchMemoriesToolHandler implements ChatReadToolHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchMemoriesToolHandler.class);
    private static final int MEMORY_CONTENT_CAP = 200;

    private final AssistantMemoryJpaRepository assistantMemoryRepository;
    private final ObjectMapper objectMapper;

    public SearchMemoriesToolHandler(
            AssistantMemoryJpaRepository assistantMemoryRepository, ObjectMapper objectMapper) {
        this.assistantMemoryRepository = assistantMemoryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.SEARCH_MEMORIES;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        SearchMemoriesArgs args =
                ReadToolJson.readArgs(objectMapper, inputJson, SearchMemoriesArgs.class);
        String query = args.query() == null ? "" : args.query().trim();
        List<MemoryOutput> memories =
                assistantMemoryRepository
                        .findByTenantIdAndContentContainingIgnoreCase(
                                boundTenantId, query, Pageable.ofSize(20))
                        .stream()
                        .map(SearchMemoriesToolHandler::toOutput)
                        .toList();
        log.info(
                "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                tenantId,
                name().id(),
                memories.size());
        return ReadToolJson.writeOutput(objectMapper, new SearchMemoriesOutput(memories));
    }

    private static MemoryOutput toOutput(AssistantMemoryEntity assistantMemory) {
        return new MemoryOutput(
                assistantMemory.getMemoryId(),
                ReadToolJson.cap(assistantMemory.getContent(), MEMORY_CONTENT_CAP),
                assistantMemory.getCreatedAt());
    }

    public record SearchMemoriesOutput(List<MemoryOutput> memories) {}

    public record MemoryOutput(UUID id, String content, Instant createdAt) {}
}
