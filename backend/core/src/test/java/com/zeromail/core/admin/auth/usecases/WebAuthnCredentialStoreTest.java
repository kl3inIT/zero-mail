package com.zeromail.core.admin.auth.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.exception.AdminAuthException;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WebAuthnCredentialStoreTest extends PostgresContainerTest {

    @Autowired private AdminUserRepository adminUserRepository;

    @Autowired private WebAuthnCredentialStore webAuthnCredentialStore;

    @Test
    void credential_store_round_trips_bytes_and_updates_counter() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000851");
        adminUserRepository.save(
                new AdminUserEntity(
                        adminUserId,
                        "credential-store@example.com",
                        "Credential Store",
                        new byte[] {0x61},
                        AdminStatus.PENDING_ENROLLMENT));

        webAuthnCredentialStore.saveCredential(
                adminUserId,
                new byte[] {0x62},
                new byte[] {0x63},
                new byte[] {0x64},
                new byte[] {0x65},
                4L,
                UUID.fromString("00000000-0000-4000-8000-000000000852"),
                "none");

        var credential =
                webAuthnCredentialStore.findByCredentialId(new byte[] {0x62}).orElseThrow();
        assertThat(credential.publicKeyCose()).containsExactly(0x63);
        assertThat(credential.attestationObject()).containsExactly(0x64);
        assertThat(credential.attestationClientDataJson()).containsExactly(0x65);
        webAuthnCredentialStore.verifyAndUpdateSignatureCounter(
                new byte[] {0x62}, 5L, Instant.parse("2026-05-19T18:36:17Z"));
        assertThat(adminUserRepository.findById(adminUserId).orElseThrow().getSignatureCounter())
                .isEqualTo(5L);
    }

    @Test
    void credential_store_rejects_replayed_counter() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000853");
        AdminUserEntity activeAdminUser =
                new AdminUserEntity(
                        adminUserId,
                        "replay-store@example.com",
                        "Replay Store",
                        new byte[] {0x64},
                        AdminStatus.ACTIVE);
        activeAdminUser.activate(
                new byte[] {0x65},
                new byte[] {0x66},
                new byte[] {0x67},
                new byte[] {0x68},
                7L,
                UUID.fromString("00000000-0000-4000-8000-000000000854"),
                "none");
        adminUserRepository.save(activeAdminUser);

        assertThatThrownBy(
                        () ->
                                webAuthnCredentialStore.verifyAndUpdateSignatureCounter(
                                        new byte[] {0x65},
                                        7L,
                                        Instant.parse("2026-05-19T18:36:17Z")))
                .isInstanceOf(AdminAuthException.class)
                .hasMessageContaining("replay");
    }
}
