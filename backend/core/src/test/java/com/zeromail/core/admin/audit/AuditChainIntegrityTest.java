package com.zeromail.core.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.audit.usecases.HmacChainHasher;
import com.zeromail.core.admin.audit.usecases.HmacChainHasher.AuditChainEntry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditChainIntegrityTest {

    private static final byte[] SECRET =
            "phase-8-audit-secret-32-byte-fixture".getBytes(StandardCharsets.UTF_8);

    @Test
    void hmac_chain_verification_detects_first_mutated_chain_index() {
        HmacChainHasher hmacChainHasher = new HmacChainHasher();

        List<AuditChainEntry> unsignedEntries =
                List.of(
                        entry(1L, "ADMIN_BOOTSTRAP_CREATED", "bootstrap created"),
                        entry(2L, "ADMIN_ROLE_GRANTED", "second admin granted"),
                        entry(3L, "ADMIN_ROLE_REVOKED", "second admin revoked"));

        List<AuditChainEntry> sealedEntries = hmacChainHasher.seal(SECRET, unsignedEntries);
        assertThat(hmacChainHasher.findFirstMismatch(SECRET, sealedEntries)).isEmpty();

        AuditChainEntry tamperedEntry = sealedEntries.get(1).withReason("tampered after insert");
        List<AuditChainEntry> tamperedEntries =
                List.of(sealedEntries.get(0), tamperedEntry, sealedEntries.get(2));

        OptionalLong firstMismatch = hmacChainHasher.findFirstMismatch(SECRET, tamperedEntries);

        assertThat(firstMismatch).hasValue(2L);
    }

    @Test
    void canonical_hash_changes_when_chain_index_changes() {
        HmacChainHasher hmacChainHasher = new HmacChainHasher();

        byte[] firstHash =
                hmacChainHasher.computeHash(SECRET, new byte[0], entry(1L, "ACTION", "reason"));
        byte[] secondHash =
                hmacChainHasher.computeHash(SECRET, new byte[0], entry(2L, "ACTION", "reason"));

        assertThat(HexFormat.of().formatHex(firstHash))
                .isNotEqualTo(HexFormat.of().formatHex(secondHash));
    }

    private static AuditChainEntry entry(long chainIndex, String action, String reason) {
        return new AuditChainEntry(
                chainIndex,
                UUID.fromString("00000000-0000-4000-8000-000000000801"),
                "admin@example.com",
                action,
                "ADMIN_USER",
                UUID.fromString("00000000-0000-4000-8000-000000000802"),
                "{\"before\":false}",
                "{\"after\":true}",
                reason,
                "127.0.0.1",
                UUID.fromString("00000000-0000-4000-8000-000000000803"),
                Instant.parse("2026-05-19T18:36:17Z").toEpochMilli(),
                new byte[0]);
    }
}
