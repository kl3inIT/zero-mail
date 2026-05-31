package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.dto.triage.AuditEntryResponse;
import com.zeromail.core.triage.projection.AuditLogRow;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AuditEntryResponseSafetyNetFieldTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void auditEntryResponseSerializesBlockedSafetyNetPatternWhenPresent() throws Exception {
        AuditEntryResponse response = AuditEntryResponse.from(row("@evilcorp.com"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("blockedBySafetyNetPattern").asString()).isEqualTo("@evilcorp.com");
    }

    @Test
    void auditEntryResponseSerializesNullBlockedSafetyNetPatternWhenAbsent() throws Exception {
        AuditEntryResponse response = AuditEntryResponse.from(row(null));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("blockedBySafetyNetPattern").isNull()).isTrue();
    }

    private static AuditLogRow row(String blockedBySafetyNetPattern) {
        return new AuditLogRow(
                UUID.randomUUID(),
                "gmail-thread-id",
                "gmail-message-id",
                "Subject excerpt",
                "sender@example.com",
                "Archive VIP",
                "ARCHIVE",
                "ruleIds=rule;evidenceIds=sender",
                "APPLIED",
                Instant.parse("2026-05-26T00:00:00Z"),
                null,
                null,
                blockedBySafetyNetPattern);
    }
}
