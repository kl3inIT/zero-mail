package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.CleanupSenderStatus;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.usecases.RuleCompileResult;
import com.zeromail.core.rules.usecases.RuleCreateCommand;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@Transactional
public class CleanupSenderActionService {

    private static final Logger log = LoggerFactory.getLogger(CleanupSenderActionService.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final String SELECT_HISTORY_MESSAGE_IDS_SQL =
            """
                    SELECT DISTINCT gmail_message_id
                    FROM mail_message_observed
                    WHERE tenant_id = ? AND sender_email = ?
                    ORDER BY gmail_message_id ASC
                    """;

    private final JdbcTemplate jdbcTemplate;
    private final CleanupSenderStatusService cleanupSenderStatusService;
    private final RuleManagementService ruleManagementService;
    private final GmailConnectionService gmailConnectionService;
    private final TriageGmailWriter triageGmailWriter;

    public CleanupSenderActionService(
            JdbcTemplate jdbcTemplate,
            CleanupSenderStatusService cleanupSenderStatusService,
            RuleManagementService ruleManagementService,
            GmailConnectionService gmailConnectionService,
            TriageGmailWriter triageGmailWriter) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.cleanupSenderStatusService =
                Objects.requireNonNull(
                        cleanupSenderStatusService, "cleanupSenderStatusService must not be null");
        this.ruleManagementService =
                Objects.requireNonNull(
                        ruleManagementService, "ruleManagementService must not be null");
        this.gmailConnectionService =
                Objects.requireNonNull(
                        gmailConnectionService, "gmailConnectionService must not be null");
        this.triageGmailWriter =
                Objects.requireNonNull(triageGmailWriter, "triageGmailWriter must not be null");
    }

    public BulkSenderActionResult approve(UUID tenantId, Collection<String> senderEmails) {
        List<String> normalizedSenderEmails = normalize(senderEmails);
        cleanupSenderStatusService.setStatus(
                tenantId, normalizedSenderEmails, CleanupSenderStatus.APPROVED);
        return result(normalizedSenderEmails, 0, 0, "approve", tenantId);
    }

    public BulkSenderActionResult unapprove(UUID tenantId, Collection<String> senderEmails) {
        List<String> normalizedSenderEmails = normalize(senderEmails);
        cleanupSenderStatusService.clearStatus(tenantId, normalizedSenderEmails);
        return result(normalizedSenderEmails, 0, 0, "unapprove", tenantId);
    }

    public BulkSenderActionResult markUnsubscribed(UUID tenantId, Collection<String> senderEmails) {
        List<String> normalizedSenderEmails = normalize(senderEmails);
        cleanupSenderStatusService.setStatus(
                tenantId, normalizedSenderEmails, CleanupSenderStatus.UNSUBSCRIBED);
        return result(normalizedSenderEmails, 0, 0, "mark_unsubscribed", tenantId);
    }

    public BulkSenderActionResult autoArchive(UUID tenantId, Collection<String> senderEmails) {
        List<String> normalizedSenderEmails = normalize(senderEmails);
        for (String senderEmail : normalizedSenderEmails) {
            createOrEnableFutureRule(
                    tenantId,
                    senderEmail,
                    "Auto archive " + senderEmail,
                    "Archive future emails from " + senderEmail,
                    archiveActions(),
                    "cleanup:auto-archive:" + stableKey(senderEmail));
        }
        BulkSenderActionResult archiveResult = archiveHistory(tenantId, normalizedSenderEmails);
        cleanupSenderStatusService.setStatus(
                tenantId, normalizedSenderEmails, CleanupSenderStatus.AUTO_ARCHIVED);
        return result(
                normalizedSenderEmails,
                archiveResult.affectedMessageCount(),
                archiveResult.failedMessageCount(),
                "auto_archive",
                tenantId);
    }

    public BulkSenderActionResult archiveHistory(UUID tenantId, Collection<String> senderEmails) {
        List<String> normalizedSenderEmails = normalize(senderEmails);
        MessageMutationCounts mutationCounts =
                mutateHistoryMessages(
                        tenantId, normalizedSenderEmails, triageGmailWriter::archiveSkipInbox);
        return result(
                normalizedSenderEmails,
                mutationCounts.affectedMessageCount(),
                mutationCounts.failedMessageCount(),
                "archive_history",
                tenantId);
    }

    public BulkSenderActionResult trashHistory(UUID tenantId, Collection<String> senderEmails) {
        List<String> normalizedSenderEmails = normalize(senderEmails);
        MessageMutationCounts mutationCounts =
                mutateHistoryMessages(
                        tenantId, normalizedSenderEmails, triageGmailWriter::moveToTrash);
        return result(
                normalizedSenderEmails,
                mutationCounts.affectedMessageCount(),
                mutationCounts.failedMessageCount(),
                "trash_history",
                tenantId);
    }

    public BulkSenderActionResult labelFuture(
            UUID tenantId, Collection<String> senderEmails, String labelName) {
        List<String> normalizedSenderEmails = normalize(senderEmails);
        String normalizedLabelName = requireLabelName(labelName);
        for (String senderEmail : normalizedSenderEmails) {
            createOrEnableFutureRule(
                    tenantId,
                    senderEmail,
                    "Label " + senderEmail,
                    "Label future emails from " + senderEmail + " as " + normalizedLabelName,
                    labelActions(normalizedLabelName),
                    "cleanup:label:" + stableKey(senderEmail + ":" + normalizedLabelName));
        }
        return result(normalizedSenderEmails, 0, 0, "label_future", tenantId);
    }

    private MessageMutationCounts mutateHistoryMessages(
            UUID tenantId, List<String> senderEmails, GmailMessageMutation mutation) {
        int affectedMessageCount = 0;
        int failedMessageCount = 0;
        for (String senderEmail : senderEmails) {
            List<String> gmailMessageIds =
                    jdbcTemplate.queryForList(
                            SELECT_HISTORY_MESSAGE_IDS_SQL, String.class, tenantId, senderEmail);
            for (String gmailMessageId : gmailMessageIds) {
                try {
                    mutation.apply(tenantId, gmailMessageId);
                    affectedMessageCount++;
                } catch (IOException gmailWriteFailure) {
                    failedMessageCount++;
                    log.warn(
                            "event=cleanup_sender_history_mutation_failed tenantId={} opFailure=true",
                            tenantId);
                }
            }
        }
        return new MessageMutationCounts(affectedMessageCount, failedMessageCount);
    }

    private void createOrEnableFutureRule(
            UUID tenantId,
            String senderEmail,
            String displayName,
            String sourceText,
            ArrayNode actionIntents,
            String templateKey) {
        RuleCompileResult compileResult =
                RuleCompileResult.compiled(
                        RuleLanguage.UNKNOWN,
                        displayName,
                        RuleSchemaVersion.RULES_V1,
                        writeJson(senderMatcher(senderEmail)),
                        writeJson(actionIntents));
        UUID gmailConnectionId = primaryGmailConnectionIdOrThrow(tenantId);
        ruleManagementService.createOrEnable(
                new RuleCreateCommand(
                        null,
                        tenantId,
                        gmailConnectionId,
                        displayName,
                        sourceText,
                        compileResult,
                        templateKey,
                        1));
    }

    private UUID primaryGmailConnectionIdOrThrow(UUID tenantId) {
        // TODO(Plan 05): replace this primary mailbox bridge with the cleanup source mailbox.
        return gmailConnectionService
                .primaryMailboxRef(tenantId)
                .map(MailboxRef::gmailConnectionId)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Primary Gmail mailbox is required for cleanup rule creation"));
    }

    private static ObjectNode senderMatcher(String senderEmail) {
        ObjectNode matcher = OBJECT_MAPPER.createObjectNode();
        matcher.put("schemaVersion", RuleSchemaVersion.RULES_V1_ID);
        matcher.put("type", "SENDER_EMAIL");
        matcher.put("nodeId", "sender");
        matcher.put("email", senderEmail);
        return matcher;
    }

    private static ArrayNode archiveActions() {
        ArrayNode actions = OBJECT_MAPPER.createArrayNode();
        ObjectNode archiveAction = OBJECT_MAPPER.createObjectNode();
        archiveAction.put("type", "archive");
        actions.add(archiveAction);
        return actions;
    }

    private static ArrayNode labelActions(String labelName) {
        ArrayNode actions = OBJECT_MAPPER.createArrayNode();
        ObjectNode labelAction = OBJECT_MAPPER.createObjectNode();
        labelAction.put("type", "label");
        labelAction.put("labelName", labelName);
        actions.add(labelAction);
        return actions;
    }

    private static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JacksonException jsonFailure) {
            throw new IllegalStateException("Failed to serialize cleanup rule JSON", jsonFailure);
        }
    }

    private static String stableKey(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String requireLabelName(String labelName) {
        if (labelName == null || labelName.isBlank()) {
            throw new IllegalArgumentException("labelName must not be blank");
        }
        String normalizedLabelName = labelName.trim();
        if (normalizedLabelName.length() > 500) {
            throw new IllegalArgumentException("labelName is too long");
        }
        return normalizedLabelName;
    }

    private static List<String> normalize(Collection<String> senderEmails) {
        List<String> normalizedSenderEmails =
                CleanupSenderStatusService.normalizeSenderEmails(senderEmails);
        if (normalizedSenderEmails.isEmpty()) {
            throw new IllegalArgumentException("senderEmails must not be empty");
        }
        return normalizedSenderEmails;
    }

    private static BulkSenderActionResult result(
            List<String> senderEmails,
            int affectedMessageCount,
            int failedMessageCount,
            String action,
            UUID tenantId) {
        log.info(
                "event=cleanup_sender_action tenantId={} action={} senderCount={} affectedMessageCount={} failedMessageCount={}",
                tenantId,
                action,
                senderEmails.size(),
                affectedMessageCount,
                failedMessageCount);
        return new BulkSenderActionResult(
                senderEmails.size(), affectedMessageCount, failedMessageCount);
    }

    public record BulkSenderActionResult(
            int senderCount, int affectedMessageCount, int failedMessageCount) {}

    private record MessageMutationCounts(int affectedMessageCount, int failedMessageCount) {}

    @FunctionalInterface
    private interface GmailMessageMutation {
        void apply(UUID tenantId, String gmailMessageId) throws IOException;
    }
}
