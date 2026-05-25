package com.zeromail.core.admin.billing.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.billing.persistence.lowlevel.BillingPackageAdminReadRepository;
import com.zeromail.core.admin.billing.projection.BillingPackageAdminRow;
import com.zeromail.core.admin.billing.projection.BillingPackageAdminStats;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.billing.persistence.BillingPackageEntity;
import com.zeromail.core.billing.persistence.BillingPackageRepository;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingPackageAdminService {

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final int MAX_INCLUDED_FEATURES = 8;
    private static final int MAX_INCLUDED_FEATURE_LENGTH = 120;
    private static final Pattern PACKAGE_CODE_PATTERN = Pattern.compile("^PKG_[A-Z0-9_]{1,60}$");

    private final BillingPackageRepository billingPackageRepository;
    private final BillingPackageAdminReadRepository billingPackageAdminReadRepository;
    private final AdminAuditWriter adminAuditWriter;

    public BillingPackageAdminService(
            BillingPackageRepository billingPackageRepository,
            BillingPackageAdminReadRepository billingPackageAdminReadRepository,
            AdminAuditWriter adminAuditWriter) {
        this.billingPackageRepository =
                Objects.requireNonNull(
                        billingPackageRepository, "billingPackageRepository must not be null");
        this.billingPackageAdminReadRepository =
                Objects.requireNonNull(
                        billingPackageAdminReadRepository,
                        "billingPackageAdminReadRepository must not be null");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
    }

    @Transactional(readOnly = true)
    public List<BillingPackageAdminRow> listPackages() {
        AdminContext.currentOrThrow();
        List<BillingPackageEntity> packages =
                billingPackageRepository.findAllByOrderByDisplayOrderAscCodeAsc();
        Map<UUID, BillingPackageAdminStats> statsByPackageId = statsFor(packages);
        return packages.stream()
                .map(
                        billingPackage ->
                                BillingPackageAdminRow.from(
                                        billingPackage,
                                        statsByPackageId.get(billingPackage.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BillingPackageAdminRow getPackage(UUID packageId) {
        AdminContext.currentOrThrow();
        BillingPackageEntity billingPackage = findPackage(packageId);
        return BillingPackageAdminRow.from(
                billingPackage,
                billingPackageAdminReadRepository
                        .findStatsByPackageId(List.of(billingPackage.getId()))
                        .get(billingPackage.getId()));
    }

    @Transactional
    public BillingPackageAdminRow createPackage(
            String code,
            String name,
            long priceVnd,
            int creditAmount,
            String description,
            List<String> includedFeatures,
            Boolean featured,
            Boolean active,
            int displayOrder,
            String requestIp,
            UUID requestId) {
        AdminContext.currentOrThrow();
        String normalizedCode = normalizeCode(code);
        if (billingPackageRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new DuplicateBillingPackageCodeException(normalizedCode);
        }
        String normalizedName = normalizeName(name);
        String normalizedDescription = normalizeDescription(description);
        String[] normalizedIncludedFeatures = normalizeIncludedFeatures(includedFeatures);
        validatePrice(priceVnd);
        validateCreditAmount(creditAmount);
        validateDisplayOrder(displayOrder);
        BillingPackageEntity billingPackage =
                new BillingPackageEntity(
                        UUID.randomUUID(),
                        normalizedCode,
                        normalizedName,
                        priceVnd,
                        creditAmount,
                        normalizedDescription,
                        normalizedIncludedFeatures,
                        Boolean.TRUE.equals(featured),
                        active == null || active,
                        displayOrder);
        billingPackageRepository.saveAndFlush(billingPackage);
        adminAuditWriter.append(
                AdminAuditAction.BILLING_PACKAGE_CREATED,
                "billing_package",
                billingPackage.getId(),
                null,
                stateOf(billingPackage),
                "Create billing package",
                requestIp,
                requestId);
        return BillingPackageAdminRow.from(
                billingPackage, BillingPackageAdminStats.empty(billingPackage.getId()));
    }

    @Transactional
    public BillingPackageAdminRow updatePackage(
            UUID packageId,
            String name,
            long priceVnd,
            int creditAmount,
            String description,
            List<String> includedFeatures,
            boolean featured,
            boolean active,
            int displayOrder,
            String requestIp,
            UUID requestId) {
        AdminContext.currentOrThrow();
        BillingPackageEntity billingPackage = findPackage(packageId);
        Map<String, ?> beforeState = stateOf(billingPackage);
        String normalizedName = normalizeName(name);
        String normalizedDescription = normalizeDescription(description);
        String[] normalizedIncludedFeatures = normalizeIncludedFeatures(includedFeatures);
        validatePrice(priceVnd);
        validateCreditAmount(creditAmount);
        validateDisplayOrder(displayOrder);
        billingPackage.updateDetails(
                normalizedName,
                priceVnd,
                creditAmount,
                normalizedDescription,
                normalizedIncludedFeatures,
                featured,
                active,
                displayOrder);
        billingPackageRepository.saveAndFlush(billingPackage);
        adminAuditWriter.append(
                AdminAuditAction.BILLING_PACKAGE_UPDATED,
                "billing_package",
                billingPackage.getId(),
                beforeState,
                stateOf(billingPackage),
                "Update billing package",
                requestIp,
                requestId);
        return getPackage(packageId);
    }

    @Transactional
    public BillingPackageAdminRow activatePackage(
            UUID packageId, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        BillingPackageEntity billingPackage = findPackage(packageId);
        Map<String, ?> beforeState = stateOf(billingPackage);
        billingPackage.activate();
        billingPackageRepository.saveAndFlush(billingPackage);
        adminAuditWriter.append(
                AdminAuditAction.BILLING_PACKAGE_ACTIVATED,
                "billing_package",
                billingPackage.getId(),
                beforeState,
                stateOf(billingPackage),
                "Activate billing package",
                requestIp,
                requestId);
        return getPackage(packageId);
    }

    @Transactional
    public BillingPackageAdminRow deactivatePackage(
            UUID packageId, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        BillingPackageEntity billingPackage = findPackage(packageId);
        Map<String, ?> beforeState = stateOf(billingPackage);
        billingPackage.deactivate();
        billingPackageRepository.saveAndFlush(billingPackage);
        adminAuditWriter.append(
                AdminAuditAction.BILLING_PACKAGE_DEACTIVATED,
                "billing_package",
                billingPackage.getId(),
                beforeState,
                stateOf(billingPackage),
                "Deactivate billing package",
                requestIp,
                requestId);
        return getPackage(packageId);
    }

    @Transactional
    public List<BillingPackageAdminRow> reorderPackages(
            List<PackageDisplayOrderUpdate> updates, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        if (updates == null || updates.isEmpty()) {
            throw new InvalidBillingPackageRequestException(
                    "Package reorder list must not be empty");
        }
        Map<UUID, Integer> displayOrderByPackageId = new HashMap<>();
        for (PackageDisplayOrderUpdate update : updates) {
            PackageDisplayOrderUpdate orderUpdate =
                    Objects.requireNonNull(update, "package display order update must not be null");
            UUID packageId = Objects.requireNonNull(orderUpdate.packageId(), "packageId");
            validateDisplayOrder(orderUpdate.displayOrder());
            displayOrderByPackageId.put(packageId, orderUpdate.displayOrder());
        }
        List<BillingPackageEntity> packages =
                billingPackageRepository.findAllById(displayOrderByPackageId.keySet());
        if (packages.size() != displayOrderByPackageId.size()) {
            throw new BillingPackageNotFoundException();
        }
        Map<String, ?> beforeState =
                Map.of(
                        "display_orders",
                        packages.stream()
                                .map(
                                        billingPackage ->
                                                Map.of(
                                                        "id",
                                                        billingPackage.getId(),
                                                        "display_order",
                                                        billingPackage.getDisplayOrder()))
                                .toList());
        for (BillingPackageEntity billingPackage : packages) {
            billingPackage.updateDisplayOrder(displayOrderByPackageId.get(billingPackage.getId()));
        }
        billingPackageRepository.saveAllAndFlush(packages);
        Map<String, ?> afterState =
                Map.of(
                        "display_orders",
                        packages.stream()
                                .map(
                                        billingPackage ->
                                                Map.of(
                                                        "id",
                                                        billingPackage.getId(),
                                                        "display_order",
                                                        billingPackage.getDisplayOrder()))
                                .toList());
        adminAuditWriter.append(
                AdminAuditAction.BILLING_PACKAGE_REORDERED,
                "billing_package",
                null,
                beforeState,
                afterState,
                "Reorder billing packages",
                requestIp,
                requestId);
        return listPackages();
    }

    private Map<UUID, BillingPackageAdminStats> statsFor(List<BillingPackageEntity> packages) {
        return billingPackageAdminReadRepository.findStatsByPackageId(
                packages.stream().map(BillingPackageEntity::getId).toList());
    }

    private BillingPackageEntity findPackage(UUID packageId) {
        UUID targetPackageId = Objects.requireNonNull(packageId, "packageId must not be null");
        return billingPackageRepository
                .findById(targetPackageId)
                .orElseThrow(() -> new BillingPackageNotFoundException(targetPackageId));
    }

    private static String normalizeCode(String code) {
        String normalizedCode = requireText(code, "code", MAX_CODE_LENGTH);
        rejectControlCharacters(normalizedCode);
        if (!PACKAGE_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new InvalidBillingPackageRequestException(
                    "code must match ^PKG_[A-Z0-9_]{1,60}$");
        }
        return normalizedCode;
    }

    private static String normalizeName(String name) {
        return requireText(name, "name", MAX_NAME_LENGTH);
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String normalizedDescription = description.trim();
        if (normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidBillingPackageRequestException("description is too long");
        }
        return normalizedDescription;
    }

    private static String[] normalizeIncludedFeatures(List<String> includedFeatures) {
        if (includedFeatures == null || includedFeatures.isEmpty()) {
            throw new InvalidBillingPackageRequestException(
                    "includedFeatures must contain at least one item");
        }
        if (includedFeatures.size() > MAX_INCLUDED_FEATURES) {
            throw new InvalidBillingPackageRequestException("includedFeatures has too many items");
        }
        Set<String> normalizedIncludedFeatures = new LinkedHashSet<>();
        for (String includedFeature : includedFeatures) {
            String normalizedIncludedFeature =
                    requireText(includedFeature, "includedFeatures", MAX_INCLUDED_FEATURE_LENGTH);
            rejectControlCharacters(normalizedIncludedFeature);
            normalizedIncludedFeatures.add(normalizedIncludedFeature);
        }
        return normalizedIncludedFeatures.toArray(String[]::new);
    }

    private static String requireText(String value, String parameterName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidBillingPackageRequestException(parameterName + " is required");
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new InvalidBillingPackageRequestException(parameterName + " is too long");
        }
        return normalizedValue;
    }

    private static void rejectControlCharacters(String value) {
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidBillingPackageRequestException(
                    "code must not contain control characters");
        }
    }

    private static void validatePrice(long priceVnd) {
        if (priceVnd <= 0) {
            throw new InvalidBillingPackageRequestException("priceVnd must be positive");
        }
    }

    private static void validateCreditAmount(int creditAmount) {
        if (creditAmount <= 0) {
            throw new InvalidBillingPackageRequestException("creditAmount must be positive");
        }
    }

    private static void validateDisplayOrder(int displayOrder) {
        if (displayOrder < 0) {
            throw new InvalidBillingPackageRequestException("displayOrder must not be negative");
        }
    }

    private static Map<String, ?> stateOf(BillingPackageEntity billingPackage) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", billingPackage.getId());
        state.put("code", billingPackage.getCode());
        state.put("name", billingPackage.getName());
        state.put("price_vnd", billingPackage.getPriceVnd());
        state.put("credit_amount", billingPackage.getCreditAmount());
        state.put("description", billingPackage.getDescription());
        state.put("included_features", List.of(billingPackage.getIncludedFeatures()));
        state.put("featured", billingPackage.isFeatured());
        state.put("active", billingPackage.isActive());
        state.put("display_order", billingPackage.getDisplayOrder());
        return state;
    }

    public record PackageDisplayOrderUpdate(UUID packageId, int displayOrder) {}

    public static class BillingPackageNotFoundException extends AdminBusinessException {

        public BillingPackageNotFoundException() {
            super("Billing package not found");
        }

        public BillingPackageNotFoundException(UUID packageId) {
            super("Billing package not found: " + packageId);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.NOT_FOUND;
        }

        @Override
        public String errorCode() {
            return "error.admin.billing.package_not_found";
        }

        @Override
        public String logEvent() {
            return "admin_billing_package_not_found";
        }

        @Override
        public String detail() {
            return "The requested billing package does not exist.";
        }
    }

    public static class DuplicateBillingPackageCodeException extends AdminBusinessException {

        private final String code;

        public DuplicateBillingPackageCodeException(String code) {
            super("Duplicate billing package code: " + code);
            this.code = code;
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.CONFLICT;
        }

        @Override
        public String errorCode() {
            return "error.admin.billing.package_code_duplicate";
        }

        @Override
        public String logEvent() {
            return "admin_billing_package_code_duplicate";
        }

        @Override
        public String detail() {
            return "A billing package with this code already exists.";
        }

        @Override
        public Map<String, Object> params() {
            return Map.of("code", code);
        }
    }

    public static class InvalidBillingPackageRequestException extends AdminBusinessException {

        public InvalidBillingPackageRequestException(String message) {
            super(message);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.billing.package_invalid";
        }

        @Override
        public String logEvent() {
            return "admin_billing_package_invalid";
        }

        @Override
        public String detail() {
            return "The billing package request is invalid.";
        }
    }
}
