package com.zeromail.core.chat.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "assistant_knowledge_snippet")
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class AssistantKnowledgeMemoryEntity extends AbstractTenantOwnedEntity {

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    protected AssistantKnowledgeMemoryEntity() {
        // Hibernate
    }

    public AssistantKnowledgeMemoryEntity(
            UUID knowledgeMemoryId, UUID tenantId, String title, String content) {
        super(knowledgeMemoryId, tenantId);
        this.title = title;
        this.content = content;
    }

    public UUID getKnowledgeMemoryId() {
        return getId();
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
