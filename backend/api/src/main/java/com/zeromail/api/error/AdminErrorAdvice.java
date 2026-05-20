package com.zeromail.api.error;

import com.zeromail.core.admin.auth.exception.AdminAuthException;
import com.zeromail.core.admin.cat.usecases.CatalogAdminService;
import com.zeromail.core.admin.cat.usecases.CatalogSyncJobRepository;
import com.zeromail.core.admin.cat.usecases.CatalogSyncOrchestrator;
import com.zeromail.core.admin.cat.usecases.FeatureDefaultProviderService;
import com.zeromail.core.admin.cat.usecases.ModelSchemaValidator;
import com.zeromail.core.admin.mkey.usecases.MasterKeyAdminService;
import com.zeromail.core.admin.mkey.usecases.MasterKeyRateLimiter;
import com.zeromail.core.admin.tenant.usecases.TenantNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.zeromail.api.controllers.admin")
public class AdminErrorAdvice {

    @ExceptionHandler(AdminAuthException.class)
    ResponseEntity<ProblemDetail> onAdminAuthException(AdminAuthException adminAuthException) {
        return problem(HttpStatus.BAD_REQUEST, "error.admin.auth");
    }

    @ExceptionHandler(AuditExportTooLargeException.class)
    ResponseEntity<ProblemDetail> onAuditExportTooLarge(
            AuditExportTooLargeException auditExportTooLargeException) {
        return problem(HttpStatus.BAD_REQUEST, "error.admin.audit_export_too_large");
    }

    @ExceptionHandler(MasterKeyAdminService.EditSessionRequiredException.class)
    ResponseEntity<ProblemDetail> onEditSessionRequired(
            MasterKeyAdminService.EditSessionRequiredException editSessionRequiredException) {
        return problem(HttpStatus.BAD_REQUEST, "error.admin.master_key_edit_session_required");
    }

    @ExceptionHandler(MasterKeyAdminService.InvalidKeyFormatException.class)
    ResponseEntity<ProblemDetail> onInvalidKeyFormat(
            MasterKeyAdminService.InvalidKeyFormatException invalidKeyFormatException) {
        return problem(HttpStatus.BAD_REQUEST, "error.admin.master_key_invalid_format");
    }

    @ExceptionHandler(MasterKeyAdminService.MasterKeyTestFailedException.class)
    ResponseEntity<ProblemDetail> onMasterKeyTestFailed(
            MasterKeyAdminService.MasterKeyTestFailedException masterKeyTestFailedException) {
        ProblemDetail problemDetail =
                problemDetail(HttpStatus.BAD_REQUEST, "error.admin.master_key_test_failed");
        problemDetail.setProperty("result", masterKeyTestFailedException.result());
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(MasterKeyRateLimiter.RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> onMasterKeyRateLimited(
            MasterKeyRateLimiter.RateLimitExceededException rateLimitExceededException) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "error.admin.master_key_rate_limited");
    }

    @ExceptionHandler(TenantNotFoundException.class)
    ResponseEntity<ProblemDetail> onTenantNotFound(
            TenantNotFoundException tenantNotFoundException) {
        return problem(HttpStatus.NOT_FOUND, "error.admin.tenant_not_found");
    }

    @ExceptionHandler(TenantConfirmEmailMismatchException.class)
    ResponseEntity<ProblemDetail> onTenantConfirmEmailMismatch(
            TenantConfirmEmailMismatchException tenantConfirmEmailMismatchException) {
        return problem(HttpStatus.BAD_REQUEST, "error.admin.confirm_email_mismatch");
    }

    @ExceptionHandler(CatalogSyncOrchestrator.CatalogSyncAnthropicDisabledException.class)
    ResponseEntity<ProblemDetail> onCatalogSyncAnthropicDisabled(
            CatalogSyncOrchestrator.CatalogSyncAnthropicDisabledException
                    catalogSyncAnthropicDisabledException) {
        return problem(HttpStatus.BAD_REQUEST, "error.admin.catalog_sync_anthropic_disabled");
    }

    @ExceptionHandler(CatalogSyncOrchestrator.CatalogSyncNotReadyException.class)
    ResponseEntity<ProblemDetail> onCatalogSyncNotReady(
            CatalogSyncOrchestrator.CatalogSyncNotReadyException catalogSyncNotReadyException) {
        return problem(HttpStatus.CONFLICT, "error.admin.catalog_sync_not_ready");
    }

    @ExceptionHandler(CatalogSyncJobRepository.CatalogSyncJobNotFoundException.class)
    ResponseEntity<ProblemDetail> onCatalogSyncJobNotFound(
            CatalogSyncJobRepository.CatalogSyncJobNotFoundException
                    catalogSyncJobNotFoundException) {
        return problem(HttpStatus.NOT_FOUND, "error.admin.catalog_sync_job_not_found");
    }

    @ExceptionHandler(CatalogAdminService.CatalogDisablePinsUnconfirmedException.class)
    ResponseEntity<ProblemDetail> onCatalogDisablePinsUnconfirmed(
            CatalogAdminService.CatalogDisablePinsUnconfirmedException
                    catalogDisablePinsUnconfirmedException) {
        ProblemDetail problemDetail =
                problemDetail(
                        HttpStatus.BAD_REQUEST, "error.admin.catalog_disable_pins_unconfirmed");
        problemDetail.setProperty(
                "pinnedTenantCount", catalogDisablePinsUnconfirmedException.pinnedTenantCount());
        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(FeatureDefaultProviderService.CatalogModelNotFoundException.class)
    ResponseEntity<ProblemDetail> onCatalogModelNotFound(
            FeatureDefaultProviderService.CatalogModelNotFoundException
                    catalogModelNotFoundException) {
        return problem(HttpStatus.NOT_FOUND, "error.admin.catalog_model_not_found");
    }

    @ExceptionHandler(ModelSchemaValidator.CatalogModelIdInvalidException.class)
    ResponseEntity<ProblemDetail> onCatalogModelIdInvalid(
            ModelSchemaValidator.CatalogModelIdInvalidException catalogModelIdInvalidException) {
        return problem(HttpStatus.BAD_REQUEST, "error.admin.catalog_model_id_invalid");
    }

    @ExceptionHandler(ModelSchemaValidator.CatalogModelSchemaMismatchException.class)
    ResponseEntity<ProblemDetail> onCatalogModelSchemaMismatch(
            ModelSchemaValidator.CatalogModelSchemaMismatchException
                    catalogModelSchemaMismatchException) {
        return problem(HttpStatus.BAD_REQUEST, "error.admin.catalog_model_schema_mismatch");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> onAdminValidation(MethodArgumentNotValidException exception) {
        String code =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(fieldError -> fieldError.getDefaultMessage())
                        .filter("error.admin.reason_sentinel_leak"::equals)
                        .findFirst()
                        .orElseGet(
                                () ->
                                        exception.getBindingResult().getFieldErrors().stream()
                                                .map(fieldError -> fieldError.getDefaultMessage())
                                                .filter("error.admin.reason_too_short"::equals)
                                                .findFirst()
                                                .orElse("error.validation"));
        ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, code);
        problemDetail.setProperty("fieldErrors", List.of());
        return ResponseEntity.badRequest().body(problemDetail);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(problemDetail(status, code));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String code) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle("Admin request rejected");
        problemDetail.setDetail("The admin request could not be completed.");
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("params", Map.of());
        problemDetail.setProperty("message", code);
        return problemDetail;
    }
}
