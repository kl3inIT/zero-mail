package com.zeromail.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.controllers.onboarding.OnboardingController;
import com.zeromail.api.dto.onboarding.SelectTemplateRequest;
import com.zeromail.api.security.ReferralAttributionCookie;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.domain.OnboardingStep;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import com.zeromail.core.referral.projection.ReferralDashboardQuery;
import com.zeromail.core.referral.projection.ReferralDashboardSnapshot;
import com.zeromail.core.referral.projection.ReferralTenantCode;
import com.zeromail.core.referral.usecases.ReferralCampaignCreateCommand;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import com.zeromail.core.referral.usecases.ReferralDashboardQueryService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class OnboardingStateMachineTest extends ApiPostgresTestBase {

    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired OnboardingController onboarding;
    @Autowired ReferralCampaignService referralCampaignService;
    @Autowired ReferralDashboardQueryService referralDashboardQueryService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanRows() {
        jdbcTemplate.execute("DELETE FROM referral_conversion");
        jdbcTemplate.execute("DELETE FROM referral_code");
        jdbcTemplate.execute("DELETE FROM referral_campaign");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void forward_transitions_allowed() {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, "t"));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            var u =
                                    users.save(
                                            new UserEntity(
                                                    UUID.randomUUID(),
                                                    tenantId,
                                                    "gs-x",
                                                    "x@example.com"));
                            // Phase 01.5 D-B1: entry state is GMAIL_CONNECTED (SIGNED_IN dropped)
                            assertThat(u.getOnboardingStep())
                                    .isEqualTo(OnboardingStep.GMAIL_CONNECTED);

                            onboarding.selectTemplate(
                                    new SelectTemplateRequest("archive-receipts"));
                            assertThat(users.findById(u.getId()).orElseThrow().getOnboardingStep())
                                    .isEqualTo(OnboardingStep.TEMPLATE_SELECTED);

                            onboarding.complete(
                                    new MockHttpServletRequest(), new MockHttpServletResponse());
                            assertThat(users.findById(u.getId()).orElseThrow().getOnboardingStep())
                                    .isEqualTo(OnboardingStep.COMPLETE);
                        });
    }

    @Test
    void onboarding_complete_qualifies_referral_cookie_for_new_tenant() {
        Instant now = Instant.now();
        Instant campaignStartsAt = now.minusSeconds(3_600);
        Instant campaignEndsAt = now.plusSeconds(86_400);
        Instant attributedAt = now.minusSeconds(1_800);
        UUID referrerTenantId = UUID.randomUUID();
        UUID referredTenantId = UUID.randomUUID();

        seedTenant(referrerTenantId, "Referrer", campaignStartsAt);
        seedTenant(referredTenantId, "Referred", attributedAt.plusSeconds(1));
        ReferralTenantCode referralTenantCode =
                referralCampaignService.getOrCreateTenantCode(
                        referralCampaignService
                                .createCampaign(
                                        new ReferralCampaignCreateCommand(
                                                "Referral Complete Test",
                                                "REF-COMPLETE-" + UUID.randomUUID(),
                                                "referral-complete-" + UUID.randomUUID(),
                                                "Complete onboarding to qualify.",
                                                ReferralCampaignStatus.ACTIVE,
                                                campaignStartsAt,
                                                campaignEndsAt,
                                                true,
                                                true,
                                                true,
                                                20))
                                .campaignId(),
                        referrerTenantId);

        ScopedValue.where(TenantContext.TENANT, referredTenantId.toString())
                .run(
                        () -> {
                            users.save(
                                    new UserEntity(
                                            UUID.randomUUID(),
                                            referredTenantId,
                                            "referred-subject-" + UUID.randomUUID(),
                                            "referred@example.test"));
                            MockHttpServletRequest request = new MockHttpServletRequest();
                            request.setCookies(
                                    referralCookie(referralTenantCode.code(), attributedAt));
                            MockHttpServletResponse response = new MockHttpServletResponse();

                            onboarding.complete(request, response);
                        });

        ReferralDashboardSnapshot dashboardSnapshot =
                referralDashboardQueryService.snapshot(
                        new ReferralDashboardQuery(
                                referralTenantCode.campaignId(),
                                campaignStartsAt,
                                campaignEndsAt,
                                20));

        assertThat(dashboardSnapshot.totalSuccessfulReferrals()).isEqualTo(1);
    }

    private Cookie referralCookie(String referralCode, Instant attributedAt) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ReferralAttributionCookie.write(response, referralCode, attributedAt, false);
        String setCookie = response.getHeader("Set-Cookie");
        String cookiePrefix = ReferralAttributionCookie.COOKIE_NAME + "=";
        int cookieStart = setCookie.indexOf(cookiePrefix) + cookiePrefix.length();
        int cookieEnd = setCookie.indexOf(';', cookieStart);
        return new Cookie(
                ReferralAttributionCookie.COOKIE_NAME, setCookie.substring(cookieStart, cookieEnd));
    }

    private void seedTenant(UUID tenantId, String displayName, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO tenants(id, display_name, created_at)
                VALUES (?, ?, ?)
                """,
                tenantId,
                displayName,
                Timestamp.from(createdAt));
    }
}
