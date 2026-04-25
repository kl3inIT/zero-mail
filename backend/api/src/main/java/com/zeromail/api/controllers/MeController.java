package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.MeResponse;
import com.zeromail.core.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    public MeResponse me() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        var user = users.findFirstByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("user not found"));
        return new MeResponse(
                user.getId().toString(),
                tenantId.toString(),
                user.getEmail(),
                user.getOnboardingStep().name());
    }
}
