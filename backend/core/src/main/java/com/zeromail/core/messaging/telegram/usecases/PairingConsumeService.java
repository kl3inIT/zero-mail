package com.zeromail.core.messaging.telegram.usecases;

import com.zeromail.core.messaging.telegram.persistence.TelegramAccountJdbcRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PairingConsumeService {

    private final PairingCodeService pairingCodeService;
    private final TelegramAccountJdbcRepository telegramAccountJdbcRepository;
    private final Clock clock;

    public PairingConsumeService(
            PairingCodeService pairingCodeService,
            TelegramAccountJdbcRepository telegramAccountJdbcRepository,
            Clock clock) {
        this.pairingCodeService = pairingCodeService;
        this.telegramAccountJdbcRepository = telegramAccountJdbcRepository;
        this.clock = clock;
    }

    @Transactional
    public UUID consume(
            String code,
            long telegramChatId,
            long telegramUserId,
            String telegramUsername,
            String languageCode) {
        UUID tenantId = pairingCodeService.verify(code).tenantId();
        telegramAccountJdbcRepository.upsertConnectedAccount(
                tenantId,
                telegramChatId,
                telegramUserId,
                telegramUsername,
                languageCode,
                clock.instant());
        return tenantId;
    }
}
