/**
 * Cleanup-domain REST controllers for the bulk-unsubscribe campaign (UNS-01..UNS-07) and the
 * per-tenant sender suppression list (UNS-02).
 *
 * <p>All controllers in this package are thin (CONVENTIONS §1): they extract {@code
 * TenantContext.currentTenantUuid()}, delegate to a service from {@code
 * com.zeromail.core.cleanup.usecases}, and map the result to a wire DTO via {@code from(...)}.
 * Business exceptions thrown by services are translated by the global {@code
 * GlobalExceptionHandler}; controller-local {@code @ExceptionHandler} methods are used only to
 * override the {@code ErrorClass}-driven default HTTP status when the SPEC mandates a non-default
 * code (UNS-03 → 400, UNS-07b → 410).
 */
package com.zeromail.api.controllers.cleanup;
