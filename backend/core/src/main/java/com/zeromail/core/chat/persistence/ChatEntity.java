package com.zeromail.core.chat.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat")
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class ChatEntity extends AbstractTenantOwnedEntity {

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "soft_deleted_at")
    private Instant softDeletedAt;

    protected ChatEntity() {
        // Hibernate
    }

    public ChatEntity(UUID chatId, UUID tenantId, String title) {
        super(chatId, tenantId);
        this.title = title;
    }

    public UUID getChatId() {
        return getId();
    }

    public String getTitle() {
        return title;
    }

    public Instant getSoftDeletedAt() {
        return softDeletedAt;
    }
}
