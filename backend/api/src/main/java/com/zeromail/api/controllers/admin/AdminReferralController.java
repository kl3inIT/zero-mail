package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.referral.AdminReferralCampaignCreateRequest;
import com.zeromail.api.dto.admin.referral.AdminReferralCampaignListResponse;
import com.zeromail.api.dto.admin.referral.AdminReferralCampaignResponse;
import com.zeromail.api.dto.admin.referral.AdminReferralCampaignStatusUpdateRequest;
import com.zeromail.api.dto.admin.referral.AdminReferralCampaignUpdateRequest;
import com.zeromail.api.dto.admin.referral.AdminReferralDashboardResponse;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.referral.projection.ReferralCampaignBannerImage;
import com.zeromail.core.referral.projection.ReferralDashboardQuery;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import com.zeromail.core.referral.usecases.ReferralDashboardQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "admin-referrals")
@RequestMapping("/api/admin/referrals")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReferralController {

    private static final Logger log = LoggerFactory.getLogger(AdminReferralController.class);
    private static final Duration STREAM_INTERVAL = Duration.ofSeconds(5);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final ReferralCampaignService referralCampaignService;
    private final ReferralDashboardQueryService referralDashboardQueryService;
    private final TaskScheduler referralDashboardTaskScheduler;

    public AdminReferralController(
            ReferralCampaignService referralCampaignService,
            ReferralDashboardQueryService referralDashboardQueryService,
            @Qualifier("referralDashboardTaskScheduler") TaskScheduler referralDashboardTaskScheduler) {
        this.referralCampaignService =
                Objects.requireNonNull(
                        referralCampaignService, "referralCampaignService must not be null");
        this.referralDashboardQueryService =
                Objects.requireNonNull(
                        referralDashboardQueryService,
                        "referralDashboardQueryService must not be null");
        this.referralDashboardTaskScheduler =
                Objects.requireNonNull(
                        referralDashboardTaskScheduler,
                        "referralDashboardTaskScheduler must not be null");
    }

    @GetMapping("/campaigns")
    public AdminReferralCampaignListResponse campaigns() {
        AdminContext.currentOrThrow();
        return AdminReferralCampaignListResponse.from(referralCampaignService.listCampaigns());
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminReferralCampaignResponse createCampaign(
            @Valid @RequestBody AdminReferralCampaignCreateRequest request) {
        AdminContext.currentOrThrow();
        return AdminReferralCampaignResponse.from(
                referralCampaignService.createCampaign(request.toCommand()));
    }

    @PutMapping("/campaigns/{campaignId}")
    public AdminReferralCampaignResponse updateCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AdminReferralCampaignUpdateRequest request) {
        AdminContext.currentOrThrow();
        return AdminReferralCampaignResponse.from(
                referralCampaignService.updateCampaign(campaignId, request.toCommand()));
    }

    @PatchMapping("/campaigns/{campaignId}/status")
    public AdminReferralCampaignResponse updateCampaignStatus(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AdminReferralCampaignStatusUpdateRequest request) {
        AdminContext.currentOrThrow();
        return AdminReferralCampaignResponse.from(
                referralCampaignService.updateCampaignStatus(campaignId, request.statusValue()));
    }

    @PutMapping(
            value = "/campaigns/{campaignId}/banner",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminReferralCampaignResponse updateCampaignBanner(
            @PathVariable UUID campaignId, @RequestPart("file") MultipartFile file) {
        AdminContext.currentOrThrow();
        return AdminReferralCampaignResponse.from(
                referralCampaignService.updateCampaignBanner(
                        campaignId, multipartBytes(file), file.getContentType()));
    }

    @GetMapping("/campaigns/{campaignId}/banner")
    public ResponseEntity<byte[]> campaignBanner(@PathVariable UUID campaignId) {
        AdminContext.currentOrThrow();
        ReferralCampaignBannerImage bannerImage =
                referralCampaignService
                        .campaignBanner(campaignId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return bannerImageResponse(bannerImage);
    }

    @GetMapping("/dashboard")
    public AdminReferralDashboardResponse dashboard(
            @RequestParam UUID campaignId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "20") int leaderboardLimit) {
        AdminContext.currentOrThrow();
        return dashboardResponse(campaignId, from, to, leaderboardLimit);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDashboard(
            @RequestParam UUID campaignId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "20") int leaderboardLimit,
            HttpServletResponse response) {
        AdminContext.currentOrThrow();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter sseEmitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        AtomicReference<ScheduledFuture<?>> futureReference = new AtomicReference<>();
        AtomicBoolean cleanupStarted = new AtomicBoolean(false);
        Runnable cleanup =
                () -> {
                    if (!cleanupStarted.compareAndSet(false, true)) {
                        return;
                    }
                    ScheduledFuture<?> scheduledFuture = futureReference.get();
                    if (scheduledFuture != null) {
                        scheduledFuture.cancel(false);
                    }
                };
        Runnable sendSnapshot =
                () -> {
                    try {
                        sseEmitter.send(
                                SseEmitter.event()
                                        .name("snapshot")
                                        .data(
                                                dashboardResponse(
                                                        campaignId, from, to, leaderboardLimit)));
                        response.flushBuffer();
                    } catch (IOException | RuntimeException streamFailure) {
                        cleanup.run();
                        log.info(
                                "event=admin_referral_stream_error reason={}",
                                streamFailure.getClass().getSimpleName());
                        sseEmitter.complete();
                    }
                };
        sendSnapshot.run();
        futureReference.set(
                referralDashboardTaskScheduler.scheduleAtFixedRate(sendSnapshot, STREAM_INTERVAL));
        sseEmitter.onCompletion(cleanup);
        sseEmitter.onTimeout(
                () -> {
                    cleanup.run();
                    sseEmitter.complete();
                });
        sseEmitter.onError(_ -> cleanup.run());
        return sseEmitter;
    }

    private AdminReferralDashboardResponse dashboardResponse(
            UUID campaignId, Instant from, Instant to, int leaderboardLimit) {
        return AdminReferralDashboardResponse.from(
                referralDashboardQueryService.snapshot(
                        new ReferralDashboardQuery(campaignId, from, to, leaderboardLimit)));
    }

    private static byte[] multipartBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException readFailure) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unable to read referral banner image", readFailure);
        }
    }

    private static ResponseEntity<byte[]> bannerImageResponse(
            ReferralCampaignBannerImage bannerImage) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bannerImage.contentType()))
                .contentLength(bannerImage.sizeBytes())
                .cacheControl(CacheControl.noCache())
                .body(bannerImage.bytes());
    }
}
