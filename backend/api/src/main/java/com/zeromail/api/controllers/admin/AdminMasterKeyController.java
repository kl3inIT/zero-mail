package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.mkey.AddProviderKeyRequest;
import com.zeromail.api.dto.admin.mkey.CreateProviderRequest;
import com.zeromail.api.dto.admin.mkey.CreateProviderResponse;
import com.zeromail.api.dto.admin.mkey.MasterKeyEditSessionResponse;
import com.zeromail.api.dto.admin.mkey.MasterKeyListResponse;
import com.zeromail.api.dto.admin.mkey.MasterKeyMaskedResponse;
import com.zeromail.api.dto.admin.mkey.MasterKeySetRequest;
import com.zeromail.api.dto.admin.mkey.ProviderKeyListResponse;
import com.zeromail.api.dto.admin.mkey.ProviderKeyResponse;
import com.zeromail.api.dto.admin.mkey.ReorderProviderKeysRequest;
import com.zeromail.api.dto.admin.mkey.RotateMasterKeyRequest;
import com.zeromail.api.dto.admin.mkey.RotationResponse;
import com.zeromail.api.dto.admin.mkey.SetFeatureDefaultRequest;
import com.zeromail.api.dto.admin.mkey.TestConnectionRequest;
import com.zeromail.api.dto.admin.mkey.TestConnectionResponse;
import com.zeromail.api.dto.admin.mkey.UpdateProviderKeyRequest;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.usecases.MasterKeyAdminService;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-master-keys")
@RequestMapping("/api/admin/master-keys")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMasterKeyController {

    private final MasterKeyAdminService masterKeyAdminService;

    public AdminMasterKeyController(MasterKeyAdminService masterKeyAdminService) {
        this.masterKeyAdminService = masterKeyAdminService;
    }

    @GetMapping({"", "/"})
    public MasterKeyListResponse list() {
        AdminContext.currentOrThrow();
        return new MasterKeyListResponse(
                masterKeyAdminService.listMasked().stream()
                        .map(MasterKeyMaskedResponse::from)
                        .toList());
    }

    @GetMapping("/{provider}")
    public MasterKeyMaskedResponse get(@PathVariable LlmProvider provider) {
        AdminContext.currentOrThrow();
        return MasterKeyMaskedResponse.from(masterKeyAdminService.getMasked(provider));
    }

    /**
     * Returns every priority-ordered credential row for a provider, including REVOKED rows. The
     * admin detail page consumes this to render the provider's failover chain.
     */
    @GetMapping("/{provider}/keys")
    public ProviderKeyListResponse listKeys(@PathVariable LlmProvider provider) {
        AdminContext.currentOrThrow();
        return new ProviderKeyListResponse(
                provider,
                masterKeyAdminService.listKeys(provider).stream()
                        .map(ProviderKeyResponse::from)
                        .toList());
    }

    @PostMapping("/{provider}/edit-session")
    public MasterKeyEditSessionResponse editSession(@PathVariable LlmProvider provider) {
        AdminContext.currentOrThrow();
        return MasterKeyEditSessionResponse.from(masterKeyAdminService.mintEditSession(provider));
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateProviderResponse createProvider(
            @Valid @RequestBody CreateProviderRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        byte[] plaintextKey = request.plaintextKey().getBytes(StandardCharsets.UTF_8);
        LlmProvider provider = LlmProvider.fromId(request.providerId());
        try {
            MasterKeyAdminService.ProviderKeyAddResult result =
                    masterKeyAdminService.createCompatibleProvider(
                            request.providerId(),
                            request.displayName(),
                            request.compatibleType(),
                            request.defaultBaseUrl(),
                            plaintextKey,
                            request.label(),
                            request.editSessionToken(),
                            httpServletRequest.getRemoteAddr(),
                            UUID.randomUUID());
            return CreateProviderResponse.from(provider.id(), result);
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    @PostMapping("/{provider}/test-connection")
    public TestConnectionResponse testConnection(
            @PathVariable LlmProvider provider,
            @Valid @RequestBody TestConnectionRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        byte[] plaintextKey = request.plaintextKey().getBytes(StandardCharsets.UTF_8);
        try {
            return new TestConnectionResponse(
                    masterKeyAdminService.testConnection(
                            provider,
                            request.keyFormat(),
                            request.baseUrl(),
                            plaintextKey,
                            request.editSessionToken(),
                            httpServletRequest.getRemoteAddr(),
                            UUID.randomUUID()));
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    @PutMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(
            @PathVariable LlmProvider provider,
            @Valid @RequestBody MasterKeySetRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        byte[] plaintextKey = request.plaintextKey().getBytes(StandardCharsets.UTF_8);
        try {
            masterKeyAdminService.set(
                    provider,
                    request.keyFormat(),
                    request.baseUrl(),
                    plaintextKey,
                    request.editSessionToken(),
                    httpServletRequest.getRemoteAddr(),
                    UUID.randomUUID());
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    @PostMapping("/{provider}/rotate")
    public RotationResponse rotate(
            @PathVariable LlmProvider provider,
            @Valid @RequestBody RotateMasterKeyRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        byte[] plaintextKey = request.plaintextKey().getBytes(StandardCharsets.UTF_8);
        try {
            return RotationResponse.from(
                    masterKeyAdminService.rotate(
                            provider,
                            request.keyFormat(),
                            request.baseUrl(),
                            plaintextKey,
                            request.editSessionToken(),
                            httpServletRequest.getRemoteAddr(),
                            UUID.randomUUID()));
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    /**
     * Appends a new credential row to the provider's failover chain. The plaintext is probed first;
     * non-OK probes are rejected. New rows land at the end of the chain (priority = max + 1).
     */
    @PostMapping("/{provider}/keys")
    public AddProviderKeyResponse addKey(
            @PathVariable LlmProvider provider,
            @Valid @RequestBody AddProviderKeyRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        byte[] plaintextKey = request.plaintextKey().getBytes(StandardCharsets.UTF_8);
        try {
            MasterKeyAdminService.ProviderKeyAddResult result =
                    masterKeyAdminService.addKey(
                            provider,
                            request.keyFormat(),
                            request.baseUrl(),
                            plaintextKey,
                            request.label(),
                            request.editSessionToken(),
                            httpServletRequest.getRemoteAddr(),
                            UUID.randomUUID());
            return new AddProviderKeyResponse(result.keyId(), result.priority());
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    /**
     * Atomically reassigns priorities 1..N within a provider. Payload must include every existing
     * key (no additions, no removals); ordering defines the new priority.
     */
    @PutMapping("/{provider}/keys/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderKeys(
            @PathVariable LlmProvider provider,
            @Valid @RequestBody ReorderProviderKeysRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        masterKeyAdminService.reorderKeys(
                provider,
                request.orderedKeyIds(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    /**
     * Probes a stored key in-place — decrypts it server-side, runs the connectivity probe, returns
     * the result. Does not require an edit session (read-only).
     */
    @PostMapping("/{provider}/keys/{keyId}/test")
    public TestConnectionResponse testKey(
            @PathVariable LlmProvider provider,
            @PathVariable UUID keyId,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return new TestConnectionResponse(
                masterKeyAdminService.testKey(
                        provider, keyId, httpServletRequest.getRemoteAddr(), UUID.randomUUID()));
    }

    /**
     * Patches operator-facing metadata (label, baseUrl) on an existing key row. No effect on
     * encrypted material, priority, or status.
     */
    @PatchMapping("/{provider}/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateKey(
            @PathVariable LlmProvider provider,
            @PathVariable UUID keyId,
            @Valid @RequestBody UpdateProviderKeyRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        masterKeyAdminService.updateKey(
                provider,
                keyId,
                request.label(),
                request.baseUrl(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    /** Deletes a single key row from the provider failover chain. */
    @DeleteMapping("/{provider}/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeKey(
            @PathVariable LlmProvider provider,
            @PathVariable UUID keyId,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        masterKeyAdminService.revokeKey(
                provider, keyId, httpServletRequest.getRemoteAddr(), UUID.randomUUID());
    }

    /**
     * Deletes an unused admin-created compatible provider. Built-in Spring AI providers are fixed.
     */
    @DeleteMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProvider(
            @PathVariable LlmProvider provider, HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        masterKeyAdminService.deleteCompatibleProvider(
                provider, httpServletRequest.getRemoteAddr(), UUID.randomUUID());
    }

    @Schema(
            name = "AddProviderKeyResponse",
            description = "Identity + assigned priority of the newly inserted credential row.",
            requiredProperties = {"keyId", "priority"})
    public record AddProviderKeyResponse(UUID keyId, int priority) {}

    @PutMapping("/feature-default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setFeatureDefault(
            @Valid @RequestBody SetFeatureDefaultRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        masterKeyAdminService.setFeatureDefault(
                request.feature(),
                request.provider(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }
}
