package com.zeromail.api.controllers;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.tenant.TriagePauseRequest;
import com.zeromail.api.dto.tenant.TriagePauseResponse;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.service.TenantService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "tenant")
public class TriagePauseController {

  private static final Logger log = LoggerFactory.getLogger(TriagePauseController.class);

  private final TenantService tenantService;

  public TriagePauseController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @PutMapping("/tenant/triage-pause")
  public TriagePauseResponse setTriagePause(@RequestBody @Valid TriagePauseRequest request) {
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    tenantService.setTriagePaused(tenantId, request.paused());
    log.info("event=triage_pause_toggled tenantId={} paused={}", tenantId, request.paused());
    return TriagePauseResponse.from(request.paused());
  }
}
