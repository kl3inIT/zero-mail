package com.zeromail.core.admin.audit.usecases;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacChainHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] EMPTY_HASH = new byte[0];

    public byte[] computeHash(
            byte[] hmacSecret, byte[] previousHash, AuditChainEntry auditChainEntry) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALGORITHM));
            mac.update(previousHash == null ? EMPTY_HASH : previousHash);
            mac.update(canonicalBytes(auditChainEntry));
            return mac.doFinal();
        } catch (java.security.GeneralSecurityException securityException) {
            throw new IllegalStateException(
                    "Unable to compute admin audit HMAC", securityException);
        }
    }

    public List<AuditChainEntry> seal(
            byte[] hmacSecret, List<AuditChainEntry> unsignedAuditChainEntries) {
        List<AuditChainEntry> sealedAuditChainEntries =
                new ArrayList<>(unsignedAuditChainEntries.size());
        byte[] previousHash = EMPTY_HASH;
        for (AuditChainEntry auditChainEntry : unsignedAuditChainEntries) {
            byte[] currentHash = computeHash(hmacSecret, previousHash, auditChainEntry);
            sealedAuditChainEntries.add(auditChainEntry.withHmacChainHash(currentHash));
            previousHash = currentHash;
        }
        return List.copyOf(sealedAuditChainEntries);
    }

    public OptionalLong findFirstMismatch(
            byte[] hmacSecret, List<AuditChainEntry> sealedAuditChainEntries) {
        byte[] previousHash = EMPTY_HASH;
        for (AuditChainEntry auditChainEntry : sealedAuditChainEntries) {
            byte[] expectedHash = computeHash(hmacSecret, previousHash, auditChainEntry);
            if (!MessageDigest.isEqual(expectedHash, auditChainEntry.hmacChainHash())) {
                return OptionalLong.of(auditChainEntry.chainIndex());
            }
            previousHash = auditChainEntry.hmacChainHash();
        }
        return OptionalLong.empty();
    }

    public String toHex(byte[] hashBytes) {
        return HexFormat.of().formatHex(hashBytes);
    }

    private byte[] canonicalBytes(AuditChainEntry auditChainEntry) {
        String canonicalValue =
                String.join(
                        "\u001f",
                        Long.toString(auditChainEntry.chainIndex()),
                        valueOf(auditChainEntry.actorUserId()),
                        textOf(auditChainEntry.actorEmail()),
                        textOf(auditChainEntry.action()),
                        textOf(auditChainEntry.targetKind()),
                        valueOf(auditChainEntry.targetId()),
                        textOf(auditChainEntry.beforeStateJson()),
                        textOf(auditChainEntry.afterStateJson()),
                        textOf(auditChainEntry.reason()),
                        textOf(auditChainEntry.requestIp()),
                        valueOf(auditChainEntry.requestId()),
                        Long.toString(auditChainEntry.canonicalTimestampMs()));
        return canonicalValue.getBytes(StandardCharsets.UTF_8);
    }

    private static String textOf(String value) {
        return value == null ? "" : value;
    }

    private static String valueOf(UUID value) {
        return value == null ? "" : value.toString();
    }

    public record AuditChainEntry(
            long chainIndex,
            UUID actorUserId,
            String actorEmail,
            String action,
            String targetKind,
            UUID targetId,
            String beforeStateJson,
            String afterStateJson,
            String reason,
            String requestIp,
            UUID requestId,
            long canonicalTimestampMs,
            byte[] hmacChainHash) {

        public AuditChainEntry {
            hmacChainHash =
                    hmacChainHash == null
                            ? EMPTY_HASH
                            : Arrays.copyOf(hmacChainHash, hmacChainHash.length);
        }

        @Override
        public byte[] hmacChainHash() {
            return Arrays.copyOf(hmacChainHash, hmacChainHash.length);
        }

        public AuditChainEntry withReason(String newReason) {
            return new AuditChainEntry(
                    chainIndex,
                    actorUserId,
                    actorEmail,
                    action,
                    targetKind,
                    targetId,
                    beforeStateJson,
                    afterStateJson,
                    newReason,
                    requestIp,
                    requestId,
                    canonicalTimestampMs,
                    hmacChainHash);
        }

        public AuditChainEntry withHmacChainHash(byte[] newHmacChainHash) {
            return new AuditChainEntry(
                    chainIndex,
                    actorUserId,
                    actorEmail,
                    action,
                    targetKind,
                    targetId,
                    beforeStateJson,
                    afterStateJson,
                    reason,
                    requestIp,
                    requestId,
                    canonicalTimestampMs,
                    newHmacChainHash);
        }
    }
}
