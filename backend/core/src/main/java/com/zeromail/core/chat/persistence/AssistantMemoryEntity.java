package com.zeromail.core.chat.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "assistant_memory")
@SuppressWarnings("JpaDataSourceORMInspection")
public class AssistantMemoryEntity extends AbstractTenantOwnedEntity {

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    protected AssistantMemoryEntity() {
        // Hibernate
    }

    public AssistantMemoryEntity(UUID memoryId, UUID tenantId, String content, String source) {
        super(memoryId, tenantId);
        this.content = content;
        this.source = source == null || source.isBlank() ? "chat" : source;
    }

    public UUID getMemoryId() {
        return getId();
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }
}
