package com.zeromail.api.controllers.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.zeromail.api.dto.integrations.TelegramDisconnectResponse;
import com.zeromail.api.dto.integrations.TelegramPairingResponse;
import com.zeromail.api.dto.integrations.TelegramStatusResponse;
import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import com.zeromail.core.messaging.telegram.domain.TelegramAccountStatus;
import com.zeromail.core.messaging.telegram.domain.TelegramAccountView;
import com.zeromail.core.messaging.telegram.usecases.DisconnectTelegramService;
import com.zeromail.core.messaging.telegram.usecases.PairingCodeService;
import com.zeromail.core.messaging.telegram.usecases.TelegramStatusQueryService;
import com.zeromail.core.tenant.TenantContext;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TelegramIntegrationControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("3c72fd8b-1536-4f5a-8d81-ad7c56fd8584");
    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");

    private final TelegramStatusQueryService telegramStatusQueryService =
            mock(TelegramStatusQueryService.class);
    private final PairingCodeService pairingCodeService = mock(PairingCodeService.class);

    @Test
    void status_returnsConnectedTelegramAccountForCurrentTenant() {
        TelegramIntegrationController controller =
                new TelegramIntegrationController(
                        configuredProperties(), telegramStatusQueryService, pairingCodeService);
        TelegramAccountView account =
                new TelegramAccountView(
                        TENANT_ID,
                        5378705410L,
                        5378705410L,
                        "nhuxuanviet",
                        "vi",
                        TelegramAccountStatus.CONNECTED,
                        NOW.minusSeconds(60),
                        NOW);
        given(telegramStatusQueryService.status(TENANT_ID))
                .willReturn(new TelegramStatusQueryService.TelegramStatus(true, account));

        TelegramStatusResponse response =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(controller::status);

        assertThat(response.configured()).isTrue();
        assertThat(response.connected()).isTrue();
        assertThat(response.telegramUsername()).isEqualTo("nhuxuanviet");
        assertThat(response.linkedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(response.lastActiveAt()).isEqualTo(NOW);
    }

    @Test
    void pairing_returnsMintedCodeForCurrentTenantWhenTelegramIsConfigured() {
        TelegramIntegrationController controller =
                new TelegramIntegrationController(
                        configuredProperties(), telegramStatusQueryService, pairingCodeService);
        given(pairingCodeService.mint(TENANT_ID))
                .willReturn(
                        new PairingCodeService.MintedPairingCode(
                                "pairing-code",
                                "https://t.me/ZeroMailBot?start=pairing-code",
                                NOW));

        TelegramPairingResponse response =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(controller::pairing);

        assertThat(response.code()).isEqualTo("pairing-code");
        assertThat(response.deeplink()).isEqualTo("https://t.me/ZeroMailBot?start=pairing-code");
        assertThat(response.expiresAt()).isEqualTo(NOW);
        then(pairingCodeService).should().mint(TENANT_ID);
    }

    @Test
    void pairing_returnsServiceUnavailableWhenTelegramIsNotConfigured() {
        TelegramIntegrationController controller =
                new TelegramIntegrationController(
                        unconfiguredProperties(), telegramStatusQueryService, pairingCodeService);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                                        .call(controller::pairing))
                .satisfies(
                        responseStatusException ->
                                assertThat(responseStatusException.getStatusCode())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void disconnect_removesTelegramConnectionForCurrentTenant() {
        DisconnectTelegramService disconnectTelegramService = mock(DisconnectTelegramService.class);
        TelegramDisconnectController controller =
                new TelegramDisconnectController(disconnectTelegramService);

        TelegramDisconnectResponse response =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(controller::disconnect);

        assertThat(response.status()).isEqualTo("DISCONNECTED");
        then(disconnectTelegramService).should().disconnect(TENANT_ID);
    }

    private static TelegramProperties configuredProperties() {
        return new TelegramProperties(
                true,
                "telegram-token",
                "ZeroMailBot",
                "telegram-bot",
                "webhook-secret",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                URI.create("https://api.telegram.org"));
    }

    private static TelegramProperties unconfiguredProperties() {
        return new TelegramProperties(
                true,
                "",
                "ZeroMailBot",
                "telegram-bot",
                "webhook-secret",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                URI.create("https://api.telegram.org"));
    }
}
