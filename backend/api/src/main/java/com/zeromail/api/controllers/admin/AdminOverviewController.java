package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.overview.AdminOverviewResponse;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.overview.projection.AdminOverviewQuery;
import com.zeromail.core.admin.overview.usecases.AdminOverviewQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-overview")
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOverviewController {

    private final AdminOverviewQueryService adminOverviewQueryService;

    public AdminOverviewController(AdminOverviewQueryService adminOverviewQueryService) {
        this.adminOverviewQueryService =
                Objects.requireNonNull(
                        adminOverviewQueryService, "adminOverviewQueryService must not be null");
    }

    @GetMapping({"", "/"})
    public AdminOverviewResponse overview(
            @RequestParam(name = "from") Instant from, @RequestParam(name = "to") Instant to) {
        AdminContext.currentOrThrow();
        return AdminOverviewResponse.from(
                adminOverviewQueryService.snapshot(new AdminOverviewQuery(from, to)));
    }
}
