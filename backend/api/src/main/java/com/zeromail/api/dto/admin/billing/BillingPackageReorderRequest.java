package com.zeromail.api.dto.admin.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record BillingPackageReorderRequest(@NotEmpty List<@Valid Item> items) {

    public record Item(@NotNull UUID packageId, @PositiveOrZero int displayOrder) {}
}
