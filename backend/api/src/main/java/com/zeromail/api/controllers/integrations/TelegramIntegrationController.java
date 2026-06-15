package com.zeromail.api.controllers.integrations;

import com.zeromail.api.dto.integrations.TelegramPairingResponse;
import com.zeromail.api.dto.integrations.TelegramStatusResponse;
import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import com.zeromail.core.messaging.telegram.domain.TelegramAccountView;
import com.zeromail.core.messaging.telegram.usecases.PairingCodeService;
import com.zeromail.core.messaging.telegram.usecases.TelegramStatusQueryService;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integrations/telegram")
public class TelegramIntegrationController {

    private final TelegramProperties telegramProperties;
    private final TelegramStatusQueryService telegramStatusQueryService;
    private final PairingCodeService pairingCodeService;

    public TelegramIntegrationController(
            TelegramProperties telegramProperties,
            TelegramStatusQueryService telegramStatusQueryService,
            PairingCodeService pairingCodeService) {
        this.telegramProperties = telegramProperties;
        this.telegramStatusQueryService = telegramStatusQueryService;
        this.pairingCodeService = pairingCodeService;
    }

    @GetMapping("/status")
    public TelegramStatusResponse status() {
        UUID tenantId = TenantContext.currentTenantUuid();
        TelegramStatusQueryService.TelegramStatus status =
                telegramStatusQueryService.status(tenantId);
        TelegramAccountView account = status.account();
        return new TelegramStatusResponse(
                status.configured(),
                status.connected(),
                account == null ? null : account.telegramUsername(),
                account == null ? null : account.linkedAt(),
                account == null ? null : account.lastActiveAt());
    }

    @PostMapping("/pairing")
    public TelegramPairingResponse pairing() {
        if (!telegramProperties.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Telegram is not configured");
        }
        PairingCodeService.MintedPairingCode mintedPairingCode =
                pairingCodeService.mint(TenantContext.currentTenantUuid());
        return new TelegramPairingResponse(
                mintedPairingCode.code(),
                mintedPairingCode.deeplink(),
                mintedPairingCode.expiresAt());
    }
}
