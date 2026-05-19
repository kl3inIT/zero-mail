package com.zeromail.core.chat.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@SuppressWarnings("unused")
public interface AssistantPendingActionJpaRepository
        extends JpaRepository<AssistantPendingActionEntity, UUID> {

    Optional<AssistantPendingActionEntity> findByChatIdAndToolCallId(
            UUID chatId, String toolCallId);

    List<AssistantPendingActionEntity> findByStateAndExpiresAtBefore(
            String state, Instant cutoff, Pageable pageable);
}
