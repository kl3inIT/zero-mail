package com.zeromail.core.triage.usecases;

import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity.PatternKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SafetyNetMatcher {

    public Optional<String> findMatch(
            String canonicalSenderEmail,
            List<TenantProtectedSenderObservationEntity> protectedSenderObservations) {
        Objects.requireNonNull(canonicalSenderEmail, "canonicalSenderEmail must not be null");
        Objects.requireNonNull(
                protectedSenderObservations, "protectedSenderObservations must not be null");

        Optional<String> exactEmailMatch =
                protectedSenderObservations.stream()
                        .filter(observation -> observation.getPatternKind() == PatternKind.EMAIL)
                        .map(TenantProtectedSenderObservationEntity::getSenderEmail)
                        .filter(canonicalSenderEmail::equals)
                        .findFirst();
        if (exactEmailMatch.isPresent()) {
            return exactEmailMatch;
        }

        return protectedSenderObservations.stream()
                .filter(observation -> observation.getPatternKind() == PatternKind.DOMAIN)
                .map(TenantProtectedSenderObservationEntity::getSenderEmail)
                .filter(domainPattern -> domainPattern.startsWith("@"))
                .filter(canonicalSenderEmail::endsWith)
                .findFirst();
    }
}
