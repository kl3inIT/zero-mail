package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.config.ZeroMailCoreProperties;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds Lemon Squeezy hosted-checkout URLs of the form {@code
 * https://{store_slug}.lemonsqueezy.com/buy/{variant_id}?checkout[email]=...&checkout[custom][tenant_id]=...}.
 *
 * <p>{@code checkout[custom][tenant_id]} is the bridge from the LS-hosted checkout back to our
 * tenant — the subscription webhook payload echoes it under {@code meta.custom_data.tenant_id} so
 * the handler can locate the right {@code subscription} row to upsert.
 *
 * <p>Returns {@code null} when the plan has no LS variant (FREE plan) or when LS is not configured
 * (test/dev). Callers must handle null gracefully.
 */
@Component
public class LemonSqueezyCheckoutUrlBuilder {

    private final ZeroMailCoreProperties.BillingProperties.LemonSqueezyProperties lemonSqueezy;

    public LemonSqueezyCheckoutUrlBuilder(ZeroMailCoreProperties coreProperties) {
        this.lemonSqueezy = coreProperties.billing().lemonSqueezy();
    }

    public String build(BillingPlanEntity plan, UUID tenantId, String userEmail) {
        if (plan.getLemonSqueezyVariantId() == null) {
            return null;
        }
        if (!lemonSqueezy.isConfigured()) {
            return null;
        }
        UriComponentsBuilder uriBuilder =
                UriComponentsBuilder.fromUriString(
                                "https://"
                                        + lemonSqueezy.storeSlug()
                                        + ".lemonsqueezy.com/buy/"
                                        + plan.getLemonSqueezyVariantId())
                        .queryParam("checkout[custom][tenant_id]", tenantId.toString());
        if (userEmail != null && !userEmail.isBlank()) {
            uriBuilder.queryParam("checkout[email]", userEmail);
        }
        return uriBuilder.build().toUriString();
    }
}
