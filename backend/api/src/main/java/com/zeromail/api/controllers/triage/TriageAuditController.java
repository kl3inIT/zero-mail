package com.zeromail.api.controllers.triage;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.triage.UndoAuditResponse;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.application.TriageUndoService;
import com.zeromail.core.triage.application.UndoAuditCommand;
import com.zeromail.core.triage.application.UndoAuditResult;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "triage")
public class TriageAuditController {

  private static final Logger log = LoggerFactory.getLogger(TriageAuditController.class);

  private final TriageUndoService triageUndoService;

  public TriageAuditController(TriageUndoService triageUndoService) {
    this.triageUndoService = triageUndoService;
  }

  @PostMapping("/api/triage/audit/{auditId}/undo")
  public UndoAuditResponse undo(@PathVariable UUID auditId) {
    UUID tenantId = currentTenantId();
    UndoAuditResult undoAuditResult =
        triageUndoService.undo(new UndoAuditCommand(auditId, tenantId));
    log.info("event=triage_undo_requested tenantId={} auditId={}", tenantId, auditId);
    return UndoAuditResponse.from(undoAuditResult);
  }

  private static UUID currentTenantId() {
    return UUID.fromString(TenantContext.currentOrThrow());
  }
}
