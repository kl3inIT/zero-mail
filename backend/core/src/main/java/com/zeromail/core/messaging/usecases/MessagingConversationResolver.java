package com.zeromail.core.messaging.usecases;

import com.zeromail.core.messaging.persistence.MessagingConversationJdbcRepository;
import com.zeromail.core.messaging.persistence.MessagingConversationJdbcRepository.ConversationLock;
import com.zeromail.core.messaging.persistence.MessagingConversationJdbcRepository.ConversationSession;
import com.zeromail.core.messaging.persistence.MessagingConversationJdbcRepository.UpsertedConversation;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves external mobile/chat conversations into stable Zero Mail chat sessions.
 *
 * <p>Transport adapters such as Telegram and Zalo should call this service with their stable
 * external conversation key. They must not infer memory from the tenant's newest web chat.
 */
@Service
public class MessagingConversationResolver {

    private final MessagingConversationJdbcRepository messagingConversationJdbcRepository;
    private final Clock clock;

    public MessagingConversationResolver(
            MessagingConversationJdbcRepository messagingConversationJdbcRepository, Clock clock) {
        this.messagingConversationJdbcRepository =
                Objects.requireNonNull(
                        messagingConversationJdbcRepository, "messagingConversationJdbcRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public MessagingConversationResolution resolveOrCreateActiveSession(
            MessagingConversationResolveCommand command) {
        Objects.requireNonNull(command, "command");
        Instant now = clock.instant();
        UUID channelAccountId = resolveChannelAccount(command, now);
        UpsertedConversation upsertedConversation =
                messagingConversationJdbcRepository.upsertConversation(
                        command.tenantId(),
                        channelAccountId,
                        command.mailAccountId(),
                        command.externalConversationId(),
                        command.externalUserId(),
                        now);
        ConversationLock conversationLock =
                messagingConversationJdbcRepository.lockConversation(
                        command.tenantId(), upsertedConversation.conversationId());
        UUID activeChatId = conversationLock.activeChatId();
        if (activeChatId == null) {
            return createSessionAndMakeActive(
                    command,
                    channelAccountId,
                    conversationLock.conversationId(),
                    upsertedConversation.inserted(),
                    now);
        }
        ConversationSession conversationSession =
                messagingConversationJdbcRepository
                        .findSessionByChatId(
                                command.tenantId(), conversationLock.conversationId(), activeChatId)
                        .orElseGet(
                                () ->
                                        createMissingSessionRow(
                                                command,
                                                conversationLock.conversationId(),
                                                activeChatId,
                                                now));
        messagingConversationJdbcRepository.touchSession(
                command.tenantId(), conversationSession.conversationSessionId(), now);
        return new MessagingConversationResolution(
                command.tenantId(),
                command.channel(),
                channelAccountId,
                conversationLock.conversationId(),
                conversationSession.conversationSessionId(),
                conversationSession.chatId(),
                command.mailAccountId(),
                upsertedConversation.inserted(),
                false);
    }

    @Transactional
    public MessagingConversationResolution startNewSession(
            MessagingConversationResolveCommand command) {
        Objects.requireNonNull(command, "command");
        Instant now = clock.instant();
        UUID channelAccountId = resolveChannelAccount(command, now);
        UpsertedConversation upsertedConversation =
                messagingConversationJdbcRepository.upsertConversation(
                        command.tenantId(),
                        channelAccountId,
                        command.mailAccountId(),
                        command.externalConversationId(),
                        command.externalUserId(),
                        now);
        ConversationLock conversationLock =
                messagingConversationJdbcRepository.lockConversation(
                        command.tenantId(), upsertedConversation.conversationId());
        return createSessionAndMakeActive(
                command,
                channelAccountId,
                conversationLock.conversationId(),
                upsertedConversation.inserted(),
                now);
    }

    private UUID resolveChannelAccount(MessagingConversationResolveCommand command, Instant now) {
        return messagingConversationJdbcRepository.upsertChannelAccount(
                command.tenantId(),
                command.channel(),
                command.externalAccountId(),
                command.channel().name(),
                now);
    }

    private MessagingConversationResolution createSessionAndMakeActive(
            MessagingConversationResolveCommand command,
            UUID channelAccountId,
            UUID conversationId,
            boolean createdConversation,
            Instant now) {
        UUID chatId = UUID.randomUUID();
        messagingConversationJdbcRepository.insertChatIfAbsent(
                command.tenantId(), chatId, command.chatTitle(), now);
        UUID conversationSessionId =
                messagingConversationJdbcRepository.insertConversationSession(
                        command.tenantId(), conversationId, chatId, command.chatTitle(), now);
        messagingConversationJdbcRepository.setActiveChat(
                command.tenantId(), conversationId, chatId, now);
        return new MessagingConversationResolution(
                command.tenantId(),
                command.channel(),
                channelAccountId,
                conversationId,
                conversationSessionId,
                chatId,
                command.mailAccountId(),
                createdConversation,
                true);
    }

    private ConversationSession createMissingSessionRow(
            MessagingConversationResolveCommand command,
            UUID conversationId,
            UUID activeChatId,
            Instant now) {
        UUID conversationSessionId =
                messagingConversationJdbcRepository.insertConversationSession(
                        command.tenantId(), conversationId, activeChatId, command.chatTitle(), now);
        return new ConversationSession(conversationSessionId, activeChatId);
    }
}
