package com.zeromail.core.triage.usecases;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.usecases.InvalidGrantException;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationRepository;
import com.zeromail.core.triage.persistence.TenantSenderOptInEntity;
import com.zeromail.core.triage.persistence.TenantSenderOptInRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Protects frequent correspondents before Gmail writes run.
 *
 * <p>A protected verdict suppresses all auto-actions on the message (label, archive, and
 * save-draft), not just destructive tiers. SPEC req 8 point b says "skip Gmail writes" with no
 * per-tier carve-out. Gmail lookup failures fail safe by returning protected=true.
 */
@Component
public class SenderSafetyNetService {

    private static final Logger log = LoggerFactory.getLogger(SenderSafetyNetService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String CACHE_KEY_PREFIX = "triage:sender-protect:";

    private final GmailApiClientFactory gmailApiClientFactory;
    private final SenderEmailCanonicalizer senderEmailCanonicalizer;
    private final TenantSenderOptInRepository tenantSenderOptInRepository;
    private final TenantProtectedSenderObservationRepository
            tenantProtectedSenderObservationRepository;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Autowired
    public SenderSafetyNetService(
            GmailApiClientFactory gmailApiClientFactory,
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            TenantSenderOptInRepository tenantSenderOptInRepository,
            TenantProtectedSenderObservationRepository tenantProtectedSenderObservationRepository,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(
                gmailApiClientFactory,
                senderEmailCanonicalizer,
                tenantSenderOptInRepository,
                tenantProtectedSenderObservationRepository,
                stringRedisTemplateProvider,
                Clock.systemUTC(),
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new));
    }

    SenderSafetyNetService(
            GmailApiClientFactory gmailApiClientFactory,
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            TenantSenderOptInRepository tenantSenderOptInRepository,
            TenantProtectedSenderObservationRepository tenantProtectedSenderObservationRepository,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.senderEmailCanonicalizer = senderEmailCanonicalizer;
        this.tenantSenderOptInRepository = tenantSenderOptInRepository;
        this.tenantProtectedSenderObservationRepository =
                tenantProtectedSenderObservationRepository;
        this.stringRedisTemplateProvider = stringRedisTemplateProvider;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public boolean isProtected(UUID tenantId, String senderEmail) {
        String canonicalSenderEmail = senderEmailCanonicalizer.canonicalize(senderEmail);
        String senderEmailHash =
                senderEmailCanonicalizer.redisCacheKeyComponent(canonicalSenderEmail);
        if (tenantSenderOptInRepository.existsByTenantIdAndSenderEmail(
                tenantId, canonicalSenderEmail)) {
            logChecked(tenantId, senderEmailHash, false, "optin");
            return false;
        }

        Optional<Boolean> cachedProtected = readCachedProtectedFlag(tenantId, senderEmailHash);
        if (cachedProtected.isPresent()) {
            meterRegistry.counter("triage.sender_safety_net.cache_hit").increment();
            boolean protectedFlag = cachedProtected.get();
            if (protectedFlag) {
                upsertProtectedObservation(tenantId, canonicalSenderEmail);
            }
            logChecked(tenantId, senderEmailHash, protectedFlag, "cache");
            return protectedFlag;
        }
        meterRegistry.counter("triage.sender_safety_net.cache_miss").increment();

        boolean protectedFlag =
                queryGmailSentHistory(tenantId, canonicalSenderEmail, senderEmailHash);
        writeCachedProtectedFlag(tenantId, senderEmailHash, protectedFlag);
        if (protectedFlag) {
            upsertProtectedObservation(tenantId, canonicalSenderEmail);
        }
        logChecked(tenantId, senderEmailHash, protectedFlag, "gmail");
        return protectedFlag;
    }

    @Transactional
    public void optInSender(UUID tenantId, String senderEmail) {
        String canonicalSenderEmail = senderEmailCanonicalizer.canonicalize(senderEmail);
        String senderEmailHash =
                senderEmailCanonicalizer.redisCacheKeyComponent(canonicalSenderEmail);
        tenantSenderOptInRepository.insertIfAbsent(
                UUID.randomUUID(), tenantId, canonicalSenderEmail, clock.instant());
        Runnable cacheDelete = () -> deleteCachedProtectedFlag(tenantId, senderEmailHash);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cacheDelete.run();
                        }
                    });
        } else {
            cacheDelete.run();
        }
        log.info(
                "event=triage_sender_opt_in tenantId={} senderEmailHash={}",
                tenantId,
                senderEmailHash);
    }

    @Transactional(readOnly = true)
    public List<ProtectedSenderListItem> listProtectedSenders(UUID tenantId) {
        Set<String> optedInSenderEmails =
                tenantSenderOptInRepository.findByTenantId(tenantId).stream()
                        .map(TenantSenderOptInEntity::getSenderEmail)
                        .collect(Collectors.toUnmodifiableSet());
        return tenantProtectedSenderObservationRepository.findByTenantId(tenantId).stream()
                .sorted(
                        Comparator.comparing(
                                TenantProtectedSenderObservationEntity::getSenderEmail))
                .map(
                        protectedSenderObservation ->
                                new ProtectedSenderListItem(
                                        protectedSenderObservation.getSenderEmail(),
                                        optedInSenderEmails.contains(
                                                protectedSenderObservation.getSenderEmail())))
                .toList();
    }

    private boolean queryGmailSentHistory(
            UUID tenantId, String canonicalSenderEmail, String senderEmailHash) {
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(tenantId);
            ListMessagesResponse response =
                    gmail.users()
                            .messages()
                            .list("me")
                            .setQ(
                                    "in:sent to:"
                                            + senderEmailCanonicalizer.gmailSearchToken(
                                                    canonicalSenderEmail)
                                            + " newer_than:90d")
                            .setMaxResults(3L)
                            .execute();
            List<Message> sentMessages = response.getMessages();
            return sentMessages != null && sentMessages.size() >= 3;
        } catch (InvalidGrantException
                | IllegalStateException
                | IOException senderSafetyNetLookupFailure) {
            logLookupFailed(tenantId, senderEmailHash);
            return true;
        }
    }

    private Optional<Boolean> readCachedProtectedFlag(UUID tenantId, String senderEmailHash) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                            stringRedisTemplate
                                    .opsForValue()
                                    .get(cacheKey(tenantId, senderEmailHash)))
                    .map(Boolean::parseBoolean);
        } catch (RuntimeException redisException) {
            log.debug(
                    "event=triage_sender_safety_net_cache_unavailable tenantId={} senderEmailHash={}",
                    tenantId,
                    senderEmailHash);
            return Optional.empty();
        }
    }

    private void writeCachedProtectedFlag(
            UUID tenantId, String senderEmailHash, boolean protectedFlag) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate
                    .opsForValue()
                    .set(
                            cacheKey(tenantId, senderEmailHash),
                            Boolean.toString(protectedFlag),
                            CACHE_TTL);
        } catch (RuntimeException redisException) {
            log.debug(
                    "event=triage_sender_safety_net_cache_unavailable tenantId={} senderEmailHash={}",
                    tenantId,
                    senderEmailHash);
        }
    }

    private void deleteCachedProtectedFlag(UUID tenantId, String senderEmailHash) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(cacheKey(tenantId, senderEmailHash));
        } catch (RuntimeException redisException) {
            log.debug(
                    "event=triage_sender_safety_net_cache_unavailable tenantId={} senderEmailHash={}",
                    tenantId,
                    senderEmailHash);
        }
    }

    private void upsertProtectedObservation(UUID tenantId, String canonicalSenderEmail) {
        tenantProtectedSenderObservationRepository.upsertObservation(
                UUID.randomUUID(), tenantId, canonicalSenderEmail, clock.instant());
    }

    private void logChecked(
            UUID tenantId, String senderEmailHash, boolean protectedFlag, String source) {
        log.info(
                "event=triage_sender_safety_net_checked tenantId={} senderEmailHash={} protected={} source={}",
                tenantId,
                senderEmailHash,
                protectedFlag,
                source);
    }

    private static void logLookupFailed(UUID tenantId, String senderEmailHash) {
        log.warn(
                "event=triage_sender_safety_net_lookup_failed tenantId={} senderEmailHash={}",
                tenantId,
                senderEmailHash);
    }

    private static String cacheKey(UUID tenantId, String senderEmailHash) {
        return CACHE_KEY_PREFIX + tenantId + ":" + senderEmailHash;
    }
}
