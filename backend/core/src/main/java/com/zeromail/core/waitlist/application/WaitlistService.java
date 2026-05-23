package com.zeromail.core.waitlist.application;

import com.zeromail.core.account.usecases.AccountService;
import com.zeromail.core.shared.crypto.Hashing;
import com.zeromail.core.waitlist.domain.WaitlistSubscribeResult;
import com.zeromail.core.waitlist.persistence.WaitlistEmailEntity;
import com.zeromail.core.waitlist.persistence.WaitlistEmailRepository;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Waitlist application service for the public pre-launch signup endpoint.
 *
 * <p>Idempotent subscribe contract — always returns a {@link WaitlistSubscribeResult}, never throws
 * on duplicates. The UNIQUE constraint on {@code waitlist_email.email} (Liquibase 086) remains the
 * authoritative race-condition guard; this service catches {@link DataIntegrityViolationException}
 * and downgrades it to {@link WaitlistSubscribeResult#ALREADY_REGISTERED} so concurrent submits do
 * not surface as 500s.
 *
 * <p><b>Privacy:</b> logs carry only an SHA-256 hex of the email; the raw value never leaves the
 * persistence layer. IP addresses are hashed with a configurable pepper (see {@code
 * zeromail.waitlist.ip-hash-pepper}) so the stored {@code ip_hash} can support abuse triage without
 * retaining the original IP.
 */
@Service
public class WaitlistService {

    private static final Logger LOG = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistEmailRepository waitlistEmailRepository;
    private final AccountService accountService;
    private final String ipHashPepper;

    public WaitlistService(
            WaitlistEmailRepository waitlistEmailRepository,
            AccountService accountService,
            @Value("${zeromail.waitlist.ip-hash-pepper:}") String ipHashPepper) {
        this.waitlistEmailRepository = waitlistEmailRepository;
        this.accountService = accountService;
        this.ipHashPepper = ipHashPepper == null ? "" : ipHashPepper;
    }

    @Transactional
    public WaitlistSubscribeResult subscribe(
            String email, String source, String ipAddress, String userAgent) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedSource = trimToNullBounded(source, 64);
        String normalizedUserAgent = trimToNullBounded(userAgent, 512);
        String emailHash = sha256Hex(normalizedEmail);

        if (accountService.isEmailRegistered(normalizedEmail)) {
            LOG.info(
                    "event=waitlist.subscribe.skipped reason=already_user emailHash={}", emailHash);
            return WaitlistSubscribeResult.ALREADY_USER;
        }
        if (waitlistEmailRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            LOG.info(
                    "event=waitlist.subscribe.skipped reason=already_registered emailHash={}",
                    emailHash);
            return WaitlistSubscribeResult.ALREADY_REGISTERED;
        }

        WaitlistEmailEntity entity =
                new WaitlistEmailEntity(
                        UUID.randomUUID(),
                        normalizedEmail,
                        normalizedSource,
                        hashIp(ipAddress),
                        normalizedUserAgent);
        try {
            waitlistEmailRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException duplicate) {
            LOG.info(
                    "event=waitlist.subscribe.race reason=already_registered emailHash={}",
                    emailHash);
            return WaitlistSubscribeResult.ALREADY_REGISTERED;
        }

        LOG.info(
                "event=waitlist.subscribed status=ADDED emailHash={} source={}",
                emailHash,
                normalizedSource);
        return WaitlistSubscribeResult.ADDED;
    }

    private String hashIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        return sha256Hex(ipAddress + ipHashPepper);
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(Hashing.sha256(value));
    }

    private static String trimToNullBounded(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
