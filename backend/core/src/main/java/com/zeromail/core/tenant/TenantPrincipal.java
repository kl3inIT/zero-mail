package com.zeromail.core.tenant;

import java.io.Serializable;

public record TenantPrincipal(String userId, String tenantId, String email) implements Serializable {
}
