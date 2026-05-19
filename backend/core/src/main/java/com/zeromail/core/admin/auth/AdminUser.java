package com.zeromail.core.admin.auth;

import java.util.UUID;

public record AdminUser(UUID id, String email, String displayName) {}
