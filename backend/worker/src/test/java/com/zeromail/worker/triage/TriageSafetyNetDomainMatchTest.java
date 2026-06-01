package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity.PatternKind;
import com.zeromail.core.triage.usecases.SafetyNetMatcher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TriageSafetyNetDomainMatchTest {

    private final SafetyNetMatcher safetyNetMatcher = new SafetyNetMatcher();

    @Test
    void domainSafetyNetEntryBlocksMatchingSenderDomain() {
        TenantProtectedSenderObservationEntity domainEntry =
                protectedSender("@acme.com", PatternKind.DOMAIN);

        assertThat(safetyNetMatcher.findMatch("ceo@acme.com", List.of(domainEntry)))
                .contains("@acme.com");
    }

    @Test
    void domainSafetyNetEntryDoesNotUseSubstringMatching() {
        TenantProtectedSenderObservationEntity domainEntry =
                protectedSender("@acme.com", PatternKind.DOMAIN);

        assertThat(safetyNetMatcher.findMatch("acme.com@evil.com", List.of(domainEntry))).isEmpty();
        assertThat(safetyNetMatcher.findMatch("ceo@notacme.com", List.of(domainEntry))).isEmpty();
        assertThat(safetyNetMatcher.findMatch("ceo@acme.com.evil.test", List.of(domainEntry)))
                .isEmpty();
    }

    @Test
    void exactEmailMatchWinsBeforeDomainMatch() {
        TenantProtectedSenderObservationEntity exactEntry =
                protectedSender("ceo@acme.com", PatternKind.EMAIL);
        TenantProtectedSenderObservationEntity domainEntry =
                protectedSender("@acme.com", PatternKind.DOMAIN);

        assertThat(safetyNetMatcher.findMatch("ceo@acme.com", List.of(exactEntry, domainEntry)))
                .contains("ceo@acme.com");
    }

    private static TenantProtectedSenderObservationEntity protectedSender(
            String pattern, PatternKind patternKind) {
        return new TenantProtectedSenderObservationEntity(
                UUID.randomUUID(), UUID.randomUUID(), pattern, Instant.now(), patternKind, true);
    }
}
