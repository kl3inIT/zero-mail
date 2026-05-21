package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.ChatToolCatalog.GetSenderSafetyEntryArgs;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationRepository;
import com.zeromail.core.triage.persistence.TenantSenderOptInEntity;
import com.zeromail.core.triage.persistence.TenantSenderOptInRepository;
import com.zeromail.core.triage.usecases.SenderEmailCanonicalizer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GetSenderSafetyEntryToolHandler implements ChatReadToolHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GetSenderSafetyEntryToolHandler.class);

    private final SenderEmailCanonicalizer senderEmailCanonicalizer;
    private final TenantProtectedSenderObservationRepository protectedSenderObservationRepository;
    private final TenantSenderOptInRepository senderOptInRepository;
    private final ObjectMapper objectMapper;

    public GetSenderSafetyEntryToolHandler(
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            TenantProtectedSenderObservationRepository protectedSenderObservationRepository,
            TenantSenderOptInRepository senderOptInRepository,
            ObjectMapper objectMapper) {
        this.senderEmailCanonicalizer = senderEmailCanonicalizer;
        this.protectedSenderObservationRepository = protectedSenderObservationRepository;
        this.senderOptInRepository = senderOptInRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.GET_SENDER_SAFETY_ENTRY;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        GetSenderSafetyEntryArgs args =
                ReadToolJson.readArgs(objectMapper, inputJson, GetSenderSafetyEntryArgs.class);
        String canonicalSenderEmail = senderEmailCanonicalizer.canonicalize(args.senderEmail());
        Optional<TenantSenderOptInEntity> senderOptIn =
                senderOptInRepository.findByTenantId(boundTenantId).stream()
                        .filter(entity -> entity.getSenderEmail().equals(canonicalSenderEmail))
                        .findFirst();
        Optional<TenantProtectedSenderObservationEntity> protectedObservation =
                protectedSenderObservationRepository.findByTenantIdAndSenderEmail(
                        boundTenantId, canonicalSenderEmail);
        SenderSafetyEntryOutput output =
                senderOptIn
                        .map(
                                entity ->
                                        output(
                                                "opted_in",
                                                entity.getCreatedAt(),
                                                canonicalSenderEmail))
                        .orElseGet(
                                () ->
                                        protectedObservation
                                                .map(
                                                        entity ->
                                                                output(
                                                                        "protected",
                                                                        entity.getFirstObservedAt(),
                                                                        canonicalSenderEmail))
                                                .orElseGet(
                                                        () ->
                                                                output(
                                                                        "not_found",
                                                                        null,
                                                                        canonicalSenderEmail)));
        log.info(
                "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                tenantId,
                name().id(),
                output.mode().equals("not_found") ? 0 : 1);
        return ReadToolJson.writeOutput(objectMapper, output);
    }

    private SenderSafetyEntryOutput output(
            String mode, Instant addedAt, String canonicalSenderEmail) {
        return new SenderSafetyEntryOutput(
                senderEmailCanonicalizer.redisCacheKeyComponent(canonicalSenderEmail),
                mode,
                addedAt);
    }

    public record SenderSafetyEntryOutput(
            String recipientEmailHash, String mode, Instant addedAt) {}
}
