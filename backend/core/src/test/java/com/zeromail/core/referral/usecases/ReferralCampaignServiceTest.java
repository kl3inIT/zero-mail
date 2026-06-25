package com.zeromail.core.referral.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import com.zeromail.core.referral.exception.ReferralCampaignActiveConflictException;
import com.zeromail.core.referral.exception.ReferralCampaignBannerInvalidException;
import com.zeromail.core.referral.exception.ReferralCampaignInactiveException;
import com.zeromail.core.referral.projection.ReferralCampaignBannerImage;
import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import com.zeromail.core.referral.projection.ReferralDashboardQuery;
import com.zeromail.core.referral.projection.ReferralDashboardSnapshot;
import com.zeromail.core.referral.projection.ReferralTenantCode;
import com.zeromail.core.referral.projection.ReferralTenantRankingSnapshot;
import com.zeromail.core.support.PostgresContainerTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class ReferralCampaignServiceTest extends PostgresContainerTest {

    private static final Path BANNER_STORAGE_DIRECTORY =
            Paths.get("build", "test-referral-banners").toAbsolutePath();
    private static final UUID REFERRER_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013001");
    private static final UUID JULY_REFERRED_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013002");
    private static final UUID AUGUST_REFERRED_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013003");
    private static final UUID PAUSED_REFERRED_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013004");
    private static final UUID RANKING_OWNER_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013005");
    private static final UUID RANKING_SECOND_OWNER_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013006");
    private static final UUID RANKING_REFERRED_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013007");
    private static final UUID RANKING_SECOND_REFERRED_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013008");
    private static final UUID RANKING_THIRD_REFERRED_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013009");
    private static final UUID EXISTING_REFERRED_TENANT_ID =
            UUID.fromString("00000000-0000-4000-8000-000000013010");
    private static final Instant JULY_START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant JULY_END = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant AUGUST_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant AUGUST_END = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant JULY_ATTRIBUTED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant AUGUST_ATTRIBUTED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ReferralCampaignService referralCampaignService;
    @Autowired ReferralDashboardQueryService referralDashboardQueryService;

    @DynamicPropertySource
    static void referralCampaignProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "zero-mail.referral.banner-storage.directory",
                () -> BANNER_STORAGE_DIRECTORY.toString());
    }

    @BeforeEach
    void cleanReferralRows() throws IOException {
        cleanBannerStorageDirectory();
        jdbcTemplate.update("DELETE FROM referral_conversion");
        jdbcTemplate.update("DELETE FROM referral_code");
        jdbcTemplate.update("DELETE FROM referral_campaign");
        jdbcTemplate.update(
                "DELETE FROM tenants WHERE id IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                REFERRER_TENANT_ID,
                JULY_REFERRED_TENANT_ID,
                AUGUST_REFERRED_TENANT_ID,
                PAUSED_REFERRED_TENANT_ID,
                RANKING_OWNER_TENANT_ID,
                RANKING_SECOND_OWNER_TENANT_ID,
                RANKING_REFERRED_TENANT_ID,
                RANKING_SECOND_REFERRED_TENANT_ID,
                RANKING_THIRD_REFERRED_TENANT_ID,
                EXISTING_REFERRED_TENANT_ID);
        seedTenant(REFERRER_TENANT_ID, "Alpha Corp", JULY_START.minusSeconds(1));
        seedTenant(JULY_REFERRED_TENANT_ID, "July Referral Tenant", JULY_START.plusSeconds(1));
        seedTenant(
                AUGUST_REFERRED_TENANT_ID, "August Referral Tenant", AUGUST_START.plusSeconds(1));
        seedTenant(PAUSED_REFERRED_TENANT_ID, "Paused Referral Tenant", JULY_START.plusSeconds(1));
        seedTenant(RANKING_OWNER_TENANT_ID, "Beta Referrer", JULY_START.minusSeconds(1));
        seedTenant(RANKING_SECOND_OWNER_TENANT_ID, "Gamma Referrer", JULY_START.minusSeconds(1));
        seedTenant(RANKING_REFERRED_TENANT_ID, "Ranking Referred One", JULY_START.plusSeconds(1));
        seedTenant(
                RANKING_SECOND_REFERRED_TENANT_ID,
                "Ranking Referred Two",
                JULY_START.plusSeconds(1));
        seedTenant(
                RANKING_THIRD_REFERRED_TENANT_ID,
                "Ranking Referred Three",
                JULY_START.plusSeconds(1));
        seedTenant(
                EXISTING_REFERRED_TENANT_ID,
                "Existing Referral Tenant",
                JULY_ATTRIBUTED_AT.minusSeconds(1));
    }

    @Test
    void successful_conversions_are_counted_once_and_isolated_by_campaign() {
        ReferralCampaignSnapshot julyCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Tháng 07/2026",
                                "REF-JULY-2026",
                                "referral-july-2026",
                                ReferralCampaignStatus.ACTIVE,
                                JULY_START,
                                JULY_END));
        ReferralTenantCode julyCode =
                referralCampaignService.getOrCreateTenantCode(
                        julyCampaign.campaignId(), REFERRER_TENANT_ID);

        referralCampaignService.qualifyConversion(
                julyCode.code(),
                JULY_REFERRED_TENANT_ID,
                JULY_ATTRIBUTED_AT,
                Instant.parse("2026-07-05T03:20:00Z"));
        referralCampaignService.qualifyConversion(
                julyCode.code(),
                JULY_REFERRED_TENANT_ID,
                JULY_ATTRIBUTED_AT,
                Instant.parse("2026-07-05T03:21:00Z"));

        ReferralDashboardSnapshot julyDashboard =
                referralDashboardQueryService.snapshot(
                        new ReferralDashboardQuery(
                                julyCampaign.campaignId(), JULY_START, JULY_END));

        assertThat(julyDashboard.totalSuccessfulReferrals()).isEqualTo(1);
        assertThat(julyDashboard.activeReferrerTenants()).isEqualTo(1);
        assertThat(julyDashboard.leaderboard()).hasSize(1);
        assertThat(julyDashboard.leaderboard().getFirst().tenantId()).isEqualTo(REFERRER_TENANT_ID);
        assertThat(julyDashboard.leaderboard().getFirst().tenantDisplayName())
                .isEqualTo("Alpha Corp");
        assertThat(julyDashboard.leaderboard().getFirst().successfulReferrals()).isEqualTo(1);

        referralCampaignService.updateCampaignStatus(
                julyCampaign.campaignId(), ReferralCampaignStatus.ENDED);

        ReferralCampaignSnapshot augustCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Tháng 08/2026",
                                "REF-AUGUST-2026",
                                "referral-august-2026",
                                ReferralCampaignStatus.ACTIVE,
                                AUGUST_START,
                                AUGUST_END));
        ReferralTenantCode augustCode =
                referralCampaignService.getOrCreateTenantCode(
                        augustCampaign.campaignId(), REFERRER_TENANT_ID);
        referralCampaignService.qualifyConversion(
                augustCode.code(),
                AUGUST_REFERRED_TENANT_ID,
                AUGUST_ATTRIBUTED_AT,
                Instant.parse("2026-08-03T02:00:00Z"));

        ReferralDashboardSnapshot refreshedJulyDashboard =
                referralDashboardQueryService.snapshot(
                        new ReferralDashboardQuery(
                                julyCampaign.campaignId(), JULY_START, JULY_END));
        ReferralDashboardSnapshot augustDashboard =
                referralDashboardQueryService.snapshot(
                        new ReferralDashboardQuery(
                                augustCampaign.campaignId(), AUGUST_START, AUGUST_END));

        assertThat(refreshedJulyDashboard.totalSuccessfulReferrals()).isEqualTo(1);
        assertThat(augustDashboard.totalSuccessfulReferrals()).isEqualTo(1);
    }

    @Test
    void active_campaign_is_globally_unique() {
        referralCampaignService.createCampaign(
                campaignCommand(
                        "Referral Tháng 07/2026",
                        "REF-JULY-2026",
                        "referral-july-2026",
                        ReferralCampaignStatus.ACTIVE,
                        JULY_START,
                        JULY_END));

        assertThatThrownBy(
                        () ->
                                referralCampaignService.createCampaign(
                                        campaignCommand(
                                                "Referral Tháng 08/2026",
                                                "REF-AUGUST-2026",
                                                "referral-august-2026",
                                                ReferralCampaignStatus.ACTIVE,
                                                AUGUST_START,
                                                AUGUST_END)))
                .isInstanceOf(ReferralCampaignActiveConflictException.class);
    }

    @Test
    void referral_page_campaign_falls_back_to_latest_ended_campaign() {
        Instant now = Instant.now();
        ReferralCampaignSnapshot endedCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Ended Test",
                                "REF-ENDED-2026",
                                "referral-ended-2026",
                                ReferralCampaignStatus.ACTIVE,
                                now.minusSeconds(7_200),
                                now.minusSeconds(3_600)));

        assertThat(referralCampaignService.referralPageCampaign())
                .hasValueSatisfying(
                        referralCampaignSnapshot ->
                                assertThat(referralCampaignSnapshot.campaignId())
                                        .isEqualTo(endedCampaign.campaignId()));
    }

    @Test
    void expired_active_campaigns_are_marked_ended_by_scheduler_use_case() {
        Instant now = Instant.now();
        ReferralCampaignSnapshot expiredActiveCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Expired Active Test",
                                "REF-EXPIRED-ACTIVE-2026",
                                "referral-expired-active-2026",
                                ReferralCampaignStatus.ACTIVE,
                                now.minusSeconds(7_200),
                                now.minusSeconds(3_600)));
        ReferralCampaignSnapshot expiredPausedCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Expired Paused Test",
                                "REF-EXPIRED-PAUSED-2026",
                                "referral-expired-paused-2026",
                                ReferralCampaignStatus.PAUSED,
                                now.minusSeconds(7_200),
                                now.minusSeconds(3_600)));

        int endedCampaignCount = referralCampaignService.endExpiredActiveCampaigns();
        int secondEndedCampaignCount = referralCampaignService.endExpiredActiveCampaigns();

        assertThat(endedCampaignCount).isEqualTo(1);
        assertThat(secondEndedCampaignCount).isZero();
        assertThat(referralCampaignService.campaign(expiredActiveCampaign.campaignId()).status())
                .isEqualTo(ReferralCampaignStatus.ENDED);
        assertThat(referralCampaignService.campaign(expiredPausedCampaign.campaignId()).status())
                .isEqualTo(ReferralCampaignStatus.PAUSED);
    }

    @Test
    void extending_expired_active_campaign_before_scheduler_tick_keeps_it_active() {
        Instant now = Instant.now();
        ReferralCampaignSnapshot campaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Time Change Test",
                                "REF-TIME-CHANGE-2026",
                                "referral-time-change-2026",
                                ReferralCampaignStatus.ACTIVE,
                                now.minusSeconds(7_200),
                                now.minusSeconds(3_600)));
        referralCampaignService.updateCampaign(
                campaign.campaignId(),
                campaignUpdateCommand(
                        "Referral Time Change Test",
                        "REF-TIME-CHANGE-2026",
                        "referral-time-change-2026",
                        now.minusSeconds(7_200),
                        now.plusSeconds(3_600)));

        int endedCampaignCount = referralCampaignService.endExpiredActiveCampaigns();

        assertThat(endedCampaignCount).isZero();
        assertThat(referralCampaignService.campaign(campaign.campaignId()).status())
                .isEqualTo(ReferralCampaignStatus.ACTIVE);
    }

    @Test
    void end_campaign_if_expired_only_updates_the_requested_expired_active_campaign() {
        Instant now = Instant.now();
        ReferralCampaignSnapshot expiredCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Single Expiry Test",
                                "REF-SINGLE-EXPIRY-2026",
                                "referral-single-expiry-2026",
                                ReferralCampaignStatus.ACTIVE,
                                now.minusSeconds(7_200),
                                now.minusSeconds(3_600)));

        boolean ended = referralCampaignService.endCampaignIfExpired(expiredCampaign.campaignId());
        boolean secondEnded =
                referralCampaignService.endCampaignIfExpired(expiredCampaign.campaignId());
        ReferralCampaignSnapshot futureCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Future Expiry Test",
                                "REF-FUTURE-EXPIRY-2026",
                                "referral-future-expiry-2026",
                                ReferralCampaignStatus.ACTIVE,
                                now.minusSeconds(3_600),
                                now.plusSeconds(3_600)));
        boolean futureEnded =
                referralCampaignService.endCampaignIfExpired(futureCampaign.campaignId());

        assertThat(ended).isTrue();
        assertThat(secondEnded).isFalse();
        assertThat(futureEnded).isFalse();
        assertThat(referralCampaignService.campaign(expiredCampaign.campaignId()).status())
                .isEqualTo(ReferralCampaignStatus.ENDED);
        assertThat(referralCampaignService.campaign(futureCampaign.campaignId()).status())
                .isEqualTo(ReferralCampaignStatus.ACTIVE);
    }

    @Test
    void activating_campaign_fails_when_another_campaign_is_active() {
        referralCampaignService.createCampaign(
                campaignCommand(
                        "Referral Tháng 07/2026",
                        "REF-JULY-2026",
                        "referral-july-2026",
                        ReferralCampaignStatus.ACTIVE,
                        JULY_START,
                        JULY_END));
        ReferralCampaignSnapshot pausedCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Tháng 08/2026",
                                "REF-AUGUST-2026",
                                "referral-august-2026",
                                ReferralCampaignStatus.PAUSED,
                                AUGUST_START,
                                AUGUST_END));

        assertThatThrownBy(
                        () ->
                                referralCampaignService.updateCampaignStatus(
                                        pausedCampaign.campaignId(), ReferralCampaignStatus.ACTIVE))
                .isInstanceOf(ReferralCampaignActiveConflictException.class);

        assertThat(referralCampaignService.campaign(pausedCampaign.campaignId()).status())
                .isEqualTo(ReferralCampaignStatus.PAUSED);
    }

    @Test
    void paused_campaign_does_not_create_successful_conversion() {
        ReferralCampaignSnapshot pausedCampaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Pause Test",
                                "REF-PAUSE-2026",
                                "referral-pause-2026",
                                ReferralCampaignStatus.PAUSED,
                                JULY_START,
                                JULY_END));
        ReferralTenantCode referralTenantCode =
                referralCampaignService.getOrCreateTenantCode(
                        pausedCampaign.campaignId(), REFERRER_TENANT_ID);

        assertThatThrownBy(
                        () ->
                                referralCampaignService.qualifyConversion(
                                        referralTenantCode.code(),
                                        PAUSED_REFERRED_TENANT_ID,
                                        JULY_ATTRIBUTED_AT,
                                        Instant.parse("2026-07-10T00:00:00Z")))
                .isInstanceOf(ReferralCampaignInactiveException.class);

        ReferralDashboardSnapshot dashboardSnapshot =
                referralDashboardQueryService.snapshot(
                        new ReferralDashboardQuery(
                                pausedCampaign.campaignId(), JULY_START, JULY_END));
        assertThat(dashboardSnapshot.totalSuccessfulReferrals()).isZero();
    }

    @Test
    void conversion_after_campaign_end_is_not_counted() {
        ReferralCampaignSnapshot campaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral End Guard Test",
                                "REF-END-GUARD-2026",
                                "referral-end-guard-2026",
                                ReferralCampaignStatus.ACTIVE,
                                JULY_START,
                                JULY_END));
        ReferralTenantCode referralTenantCode =
                referralCampaignService.getOrCreateTenantCode(
                        campaign.campaignId(), REFERRER_TENANT_ID);

        assertThatThrownBy(
                        () ->
                                referralCampaignService.qualifyConversion(
                                        referralTenantCode.code(),
                                        JULY_REFERRED_TENANT_ID,
                                        JULY_ATTRIBUTED_AT,
                                        JULY_END))
                .isInstanceOf(ReferralCampaignInactiveException.class);

        ReferralDashboardSnapshot dashboardSnapshot =
                referralDashboardQueryService.snapshot(
                        new ReferralDashboardQuery(campaign.campaignId(), JULY_START, JULY_END));
        assertThat(dashboardSnapshot.totalSuccessfulReferrals()).isZero();
    }

    @Test
    void tenant_ranking_excludes_tenants_without_successful_referrals() {
        ReferralCampaignSnapshot campaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Ranking Test",
                                "REF-RANKING-2026",
                                "referral-ranking-2026",
                                ReferralCampaignStatus.ACTIVE,
                                JULY_START,
                                JULY_END));
        referralCampaignService.getOrCreateTenantCode(
                campaign.campaignId(), PAUSED_REFERRED_TENANT_ID);
        referralCampaignService.getOrCreateTenantCode(campaign.campaignId(), REFERRER_TENANT_ID);
        ReferralTenantCode betaCode =
                referralCampaignService.getOrCreateTenantCode(
                        campaign.campaignId(), RANKING_OWNER_TENANT_ID);
        ReferralTenantCode gammaCode =
                referralCampaignService.getOrCreateTenantCode(
                        campaign.campaignId(), RANKING_SECOND_OWNER_TENANT_ID);
        referralCampaignService.getOrCreateTenantCode(
                campaign.campaignId(), AUGUST_REFERRED_TENANT_ID);

        referralCampaignService.qualifyConversion(
                betaCode.code(),
                RANKING_REFERRED_TENANT_ID,
                JULY_ATTRIBUTED_AT,
                Instant.parse("2026-07-05T03:20:00Z"));
        referralCampaignService.qualifyConversion(
                betaCode.code(),
                RANKING_SECOND_REFERRED_TENANT_ID,
                JULY_ATTRIBUTED_AT,
                Instant.parse("2026-07-05T03:21:00Z"));
        referralCampaignService.qualifyConversion(
                gammaCode.code(),
                RANKING_THIRD_REFERRED_TENANT_ID,
                JULY_ATTRIBUTED_AT,
                Instant.parse("2026-07-05T03:22:00Z"));

        ReferralTenantRankingSnapshot rankingSnapshot =
                referralDashboardQueryService.tenantRanking(
                        new ReferralDashboardQuery(campaign.campaignId(), JULY_START, JULY_END),
                        PAUSED_REFERRED_TENANT_ID);

        assertThat(rankingSnapshot.totalRankedTenants()).isEqualTo(2);
        assertThat(rankingSnapshot.currentTenant()).isNull();
        assertThat(rankingSnapshot.leaderboard())
                .allSatisfy(
                        referralLeaderboardRow ->
                                assertThat(referralLeaderboardRow.successfulReferrals())
                                        .isGreaterThan(0));
        assertThat(rankingSnapshot.leaderboard())
                .extracting(referralLeaderboardRow -> referralLeaderboardRow.tenantId())
                .doesNotContain(
                        PAUSED_REFERRED_TENANT_ID, REFERRER_TENANT_ID, AUGUST_REFERRED_TENANT_ID);
        assertThat(rankingSnapshot.leaderboard())
                .extracting(referralLeaderboardRow -> referralLeaderboardRow.rank())
                .containsExactly(1, 2);
    }

    @Test
    void conversion_is_not_counted_when_referred_tenant_existed_before_link_click() {
        ReferralCampaignSnapshot campaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Existing Tenant Test",
                                "REF-EXISTING-2026",
                                "referral-existing-2026",
                                ReferralCampaignStatus.ACTIVE,
                                JULY_START,
                                JULY_END));
        ReferralTenantCode referralTenantCode =
                referralCampaignService.getOrCreateTenantCode(
                        campaign.campaignId(), REFERRER_TENANT_ID);

        referralCampaignService.qualifyConversion(
                referralTenantCode.code(),
                EXISTING_REFERRED_TENANT_ID,
                JULY_ATTRIBUTED_AT,
                Instant.parse("2026-07-05T03:20:00Z"));
        referralCampaignService.qualifyConversion(
                referralTenantCode.code(),
                JULY_REFERRED_TENANT_ID,
                JULY_ATTRIBUTED_AT,
                Instant.parse("2026-07-05T03:21:00Z"));

        ReferralDashboardSnapshot dashboardSnapshot =
                referralDashboardQueryService.snapshot(
                        new ReferralDashboardQuery(campaign.campaignId(), JULY_START, JULY_END));

        assertThat(dashboardSnapshot.totalSuccessfulReferrals()).isEqualTo(1);
        assertThat(dashboardSnapshot.leaderboard().getFirst().successfulReferrals()).isEqualTo(1);
    }

    @Test
    void campaign_banner_upload_persists_file_reference_outside_campaign_table()
            throws IOException {
        ReferralCampaignSnapshot campaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Banner Test",
                                "REF-BANNER-2026",
                                "referral-banner-2026",
                                ReferralCampaignStatus.PAUSED,
                                JULY_START,
                                JULY_END));
        byte[] imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D};

        ReferralCampaignSnapshot updatedCampaign =
                referralCampaignService.updateCampaignBanner(
                        campaign.campaignId(), imageBytes, "image/png");

        assertThat(updatedCampaign.bannerImageAvailable()).isTrue();
        ReferralCampaignBannerImage bannerImage =
                referralCampaignService.campaignBanner(campaign.campaignId()).orElseThrow();
        assertThat(bannerImage.contentType()).isEqualTo("image/png");
        assertThat(bannerImage.sizeBytes()).isEqualTo(imageBytes.length);
        assertThat(bannerImage.bytes()).containsExactly(imageBytes);

        String objectKey =
                jdbcTemplate.queryForObject(
                        "SELECT banner_image_object_key FROM referral_campaign WHERE id = ?",
                        String.class,
                        campaign.campaignId());
        assertThat(objectKey).isNotBlank();
        assertThat(Files.readAllBytes(BANNER_STORAGE_DIRECTORY.resolve(objectKey)))
                .containsExactly(imageBytes);

        Integer binaryColumnCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'referral_campaign'
                          AND column_name = 'banner_image_bytes'
                        """,
                        Integer.class);
        assertThat(binaryColumnCount).isZero();
    }

    @Test
    void campaign_reward_configuration_round_trips_through_create_and_update() {
        ReferralCampaignSnapshot campaign =
                referralCampaignService.createCampaign(
                        new ReferralCampaignCreateCommand(
                                "Referral Reward Config Test",
                                "REF-REWARD-CONFIG-2026",
                                "referral-reward-config-2026",
                                "Reward config",
                                ReferralCampaignStatus.PAUSED,
                                JULY_START,
                                JULY_END,
                                true,
                                true,
                                true,
                                20,
                                5,
                                "Top 5 tenants receive reward instructions by email."));

        assertThat(campaign.rewardRankCutoff()).isEqualTo(5);
        assertThat(campaign.rewardNotificationText())
                .isEqualTo("Top 5 tenants receive reward instructions by email.");

        ReferralCampaignSnapshot updatedCampaign =
                referralCampaignService.updateCampaign(
                        campaign.campaignId(),
                        new ReferralCampaignUpdateCommand(
                                "Referral Reward Config Test",
                                "REF-REWARD-CONFIG-2026",
                                "referral-reward-config-2026",
                                "Reward config updated",
                                JULY_START,
                                JULY_END,
                                true,
                                true,
                                true,
                                20,
                                7,
                                "Top 7 tenants receive reward instructions by email."));

        assertThat(updatedCampaign.rewardRankCutoff()).isEqualTo(7);
        assertThat(updatedCampaign.rewardNotificationText())
                .isEqualTo("Top 7 tenants receive reward instructions by email.");
    }

    @Test
    void campaign_banner_upload_rejects_images_larger_than_five_mb() {
        ReferralCampaignSnapshot campaign =
                referralCampaignService.createCampaign(
                        campaignCommand(
                                "Referral Banner Size Test",
                                "REF-BANNER-SIZE-2026",
                                "referral-banner-size-2026",
                                ReferralCampaignStatus.PAUSED,
                                JULY_START,
                                JULY_END));
        byte[] imageBytes = new byte[(5 * 1024 * 1024) + 1];

        assertThatThrownBy(
                        () ->
                                referralCampaignService.updateCampaignBanner(
                                        campaign.campaignId(), imageBytes, "image/png"))
                .isInstanceOf(ReferralCampaignBannerInvalidException.class);
    }

    private ReferralCampaignCreateCommand campaignCommand(
            String name,
            String campaignCode,
            String slug,
            ReferralCampaignStatus status,
            Instant startsAt,
            Instant endsAt) {
        return new ReferralCampaignCreateCommand(
                name,
                campaignCode,
                slug,
                "Mời bạn bè dùng Zero Mail",
                status,
                startsAt,
                endsAt,
                true,
                true,
                true,
                20,
                3,
                "Top 3 tenants receive reward instructions by email.");
    }

    private ReferralCampaignUpdateCommand campaignUpdateCommand(
            String name, String campaignCode, String slug, Instant startsAt, Instant endsAt) {
        return new ReferralCampaignUpdateCommand(
                name,
                campaignCode,
                slug,
                "Mời bạn bè dùng Zero Mail",
                startsAt,
                endsAt,
                true,
                true,
                true,
                20,
                3,
                "Top 3 tenants receive reward instructions by email.");
    }

    private void seedTenant(UUID tenantId, String displayName, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO tenants(id, display_name, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET display_name = EXCLUDED.display_name,
                    created_at = EXCLUDED.created_at
                """,
                tenantId,
                displayName,
                Timestamp.from(createdAt));
    }

    private static void cleanBannerStorageDirectory() throws IOException {
        Files.createDirectories(BANNER_STORAGE_DIRECTORY);
        try (Stream<Path> storagePaths = Files.walk(BANNER_STORAGE_DIRECTORY)) {
            for (Path storagePath : storagePaths.sorted(Comparator.reverseOrder()).toList()) {
                if (!storagePath.equals(BANNER_STORAGE_DIRECTORY)) {
                    Files.deleteIfExists(storagePath);
                }
            }
        }
    }
}
