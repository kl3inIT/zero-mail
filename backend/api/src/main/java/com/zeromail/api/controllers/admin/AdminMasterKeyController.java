package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.mkey.MasterKeyEditSessionResponse;
import com.zeromail.api.dto.admin.mkey.MasterKeyListResponse;
import com.zeromail.api.dto.admin.mkey.MasterKeyMaskedResponse;
import com.zeromail.api.dto.admin.mkey.MasterKeySetRequest;
import com.zeromail.api.dto.admin.mkey.RotateMasterKeyRequest;
import com.zeromail.api.dto.admin.mkey.RotationResponse;
import com.zeromail.api.dto.admin.mkey.SetFeatureDefaultRequest;
import com.zeromail.api.dto.admin.mkey.TestConnectionRequest;
import com.zeromail.api.dto.admin.mkey.TestConnectionResponse;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.usecases.MasterKeyAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping("/{provider}/edit-session")
    public MasterKeyEditSessionResponse editSession(@PathVariable LlmProvider provider) {
        AdminContext.currentOrThrow();
        return MasterKeyEditSessionResponse.from(masterKeyAdminService.mintEditSession(provider));
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
