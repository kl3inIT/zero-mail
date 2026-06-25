package com.zeromail.core.admin.billing.usecases;

import com.zeromail.core.admin.billing.persistence.lowlevel.AdminBillingPackageReadRepository;
import com.zeromail.core.admin.billing.projection.AdminBillingPackageSnapshot;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBillingPackageQueryService {

    private final AdminBillingPackageReadRepository adminBillingPackageReadRepository;
    private final Clock clock;

    public AdminBillingPackageQueryService(
            AdminBillingPackageReadRepository adminBillingPackageReadRepository, Clock clock) {
        this.adminBillingPackageReadRepository =
                Objects.requireNonNull(
                        adminBillingPackageReadRepository,
                        "adminBillingPackageReadRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public AdminBillingPackageSnapshot snapshot() {
        return new AdminBillingPackageSnapshot(
                adminBillingPackageReadRepository.findPlans(),
                adminBillingPackageReadRepository.findFeaturePermissions(),
                adminBillingPackageReadRepository.findPaymentHistory(),
                clock.instant());
    }
}
