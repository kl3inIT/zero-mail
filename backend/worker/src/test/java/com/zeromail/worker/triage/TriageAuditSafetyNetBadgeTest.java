package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.TriageAuditCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TriageAuditSafetyNetBadgeTest {

    @Test
    void triageAuditCommandCarriesBlockedSafetyNetPatternWhenSafetyNetBlocksAction() {
        TriageAuditCommand command =
                new TriageAuditCommand(
                        UUID.randomUUID(),
                        "gmail-message-id",
                        "gmail-thread-id",
                        "Subject excerpt",
                        "sender@acme.com",
                        UUID.randomUUID(),
                        "Archive VIP",
                        RuleActionType.ARCHIVE,
                        new TriageActionResult.Archive(),
                        null,
                        "ruleIds=rule;evidenceIds=sender;outboundFallbackReason=SENDER_SAFETY_NET",
                        "@acme.com");

        assertThat(command.blockedBySafetyNetPattern()).isEqualTo("@acme.com");
    }
}
