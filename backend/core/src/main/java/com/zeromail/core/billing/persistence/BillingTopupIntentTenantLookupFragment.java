package com.zeromail.core.billing.persistence;

import java.util.Optional;

public interface BillingTopupIntentTenantLookupFragment {

    /**
     * Looks up an intent by code without Hibernate tenant filtering so webhook handling can
     * bind the returned tenant before the transactional JPA mutation.
     */
    Optional<BillingTopupIntentTenantLookup> findTenantLookupByCode(String code);
}
