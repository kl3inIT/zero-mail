package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.scheduler.SchedulerResponse;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.scheduler.usecases.SchedulerCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only catalog of every background scheduler in the system (across the API and WORKER
 * processes). Answers the operator question "what recurring jobs run, and when next?". Phase 1 is
 * view-only; "Trigger now" + live last-run/status arrive in scheduler phase 2.
 */
@RestController
@Tag(name = "admin-schedulers")
@RequestMapping("/api/admin/schedulers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSchedulerController {

    private final SchedulerCatalogService schedulerCatalogService;

    public AdminSchedulerController(SchedulerCatalogService schedulerCatalogService) {
        this.schedulerCatalogService =
                Objects.requireNonNull(
                        schedulerCatalogService, "schedulerCatalogService must not be null");
    }

    @GetMapping
    public List<SchedulerResponse> schedulers() {
        AdminContext.currentOrThrow();
        return schedulerCatalogService.listSchedulers().stream()
                .map(SchedulerResponse::from)
                .toList();
    }
}
