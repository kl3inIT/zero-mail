package com.zeromail.api.controllers.byok;

import com.zeromail.api.dto.byok.ByokActivateRequest;
import com.zeromail.api.dto.byok.ByokModelRequest;
import com.zeromail.api.dto.byok.ByokResponse;
import com.zeromail.api.dto.byok.ByokSaveRequest;
import com.zeromail.api.dto.byok.ByokTestConnectionResponse;
import com.zeromail.core.llm.byok.ByokSaveCommand;
import com.zeromail.core.llm.byok.UserByokService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "byok")
@RequestMapping("/api/byok")
@PreAuthorize("isAuthenticated()")
public class UserByokController {

    private final UserByokService userByokService;

    public UserByokController(UserByokService userByokService) {
        this.userByokService = userByokService;
    }

    @GetMapping({"", "/"})
    public ByokResponse get() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return userByokService
                .load(tenantId)
                .map(ByokResponse::from)
                .orElseThrow(UserByokService.ByokNoRowException::new);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<ByokResponse> save(@Valid @RequestBody ByokSaveRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        ByokResponse response =
                ByokResponse.from(
                        userByokService.save(
                                tenantId,
                                new ByokSaveCommand(
                                        request.provider(), request.baseUrl(), request.apiKey())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/active")
    public ByokResponse activate(@Valid @RequestBody ByokActivateRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return ByokResponse.from(userByokService.activate(tenantId, request.active()));
    }

    @PutMapping("/model")
    public ByokResponse setModel(@Valid @RequestBody ByokModelRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return ByokResponse.from(userByokService.setModel(tenantId, request.modelId()));
    }

    @DeleteMapping({"", "/"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete() {
        UUID tenantId = TenantContext.currentTenantUuid();
        userByokService.delete(tenantId);
    }

    @PostMapping("/test-connection")
    public ByokTestConnectionResponse testConnection() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return ByokTestConnectionResponse.from(userByokService.testConnection(tenantId));
    }
}
