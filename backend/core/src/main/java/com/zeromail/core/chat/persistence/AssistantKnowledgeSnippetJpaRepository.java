package com.zeromail.core.chat.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@SuppressWarnings("unused")
public interface AssistantKnowledgeSnippetJpaRepository
        extends JpaRepository<AssistantKnowledgeSnippetEntity, UUID> {}
