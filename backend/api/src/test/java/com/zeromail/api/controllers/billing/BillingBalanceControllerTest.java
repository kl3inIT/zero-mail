package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zeromail.api.dto.billing.BillingBalanceResponse;
import com.zeromail.api.dto.billing.BillingCheckoutResponse;
import com.zeromail.api.dto.billing.BillingLedgerHistoryResponse;
import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.billing.domain.CreditGrantCategory;
import com.zeromail.core.billing.domain.CreditGrantStatus;
import com.zeromail.core.billing.persistence.BillingCheckoutSessionRepository;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodRepository;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.billing.persistence.BillingWebhookEventEntity;
import com.zeromail.core.billing.persistence.BillingWebhookEventRepository;
import com.zeromail.core.billing.persistence.CreditGrantEntity;
import com.zeromail.core.billing.persistence.CreditGrantRepository;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "zero-mail.billing.lemon-squeezy.store-id=123",
            "zero-mail.billing.lemon-squeezy.api-key=test-lemon-api-key",
            "zero-mail.billing.lemon-squeezy.webhook-signing-secret=test-webhook-secret",
            "zero-mail.billing.lemon-squeezy.test-mode=true"
        })
@Import(TestSessionSupport.class)
class BillingBalanceControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired BillingPlanRepository billingPlanRepository;
    @Autowired BillingCheckoutSessionRepository billingCheckoutSessionRepository;
    @Autowired CreditGrantRepository creditGrantRepository;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired BillingPlanPeriodRepository billingPlanPeriodRepository;
    @Autowired BillingWebhookEventRepository billingWebhookEventRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;
    @Autowired RestClient.Builder lemonSqueezyRestClientBuilder;

    private MockRestServiceServer lemonSqueezyServer;

    @BeforeEach
    void setUpLemonSqueezyServer() {
        lemonSqueezyServer = MockRestServiceServer.bindTo(lemonSqueezyRestClientBuilder).build();
    }

    @Test
    void authenticated_balance_returns_shape() {
        Seed seed = seedUser("billing-balance");
        seedTopup(seed.tenantId(), 42);
        seedAdditionalGrant(seed.tenantId(), CreditGrantCategory.PROMOTIONAL, 7);
        seedAdditionalGrant(seed.tenantId(), CreditGrantCategory.ADMIN, 3);

        BillingBalanceResponse response =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/credits/balance")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .body(BillingBalanceResponse.class);

        assertThat(response.availableCredits()).isEqualTo(352);
        assertThat(response.heldCredits()).isZero();
        assertThat(response.currency()).isEqualTo("credits");
        assertThat(response.monthlyCredits()).isEqualTo(300);
        assertThat(response.additionalCredits()).isEqualTo(52);
        assertThat(response.monthlyCreditAllowance()).isEqualTo(300);
        assertThat(response.resetsAt()).isNotNull();
    }

    @Test
    void authenticated_ledger_returns_recent_credit_activity() {
        Seed seed = seedUser("billing-ledger");
        seedTopup(seed.tenantId(), 42);

        BillingLedgerHistoryResponse response =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/credits/ledger?limit=10")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .body(BillingLedgerHistoryResponse.class);

        assertThat(response.entries()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.entries())
                .anySatisfy(
                        ledgerEntry -> {
                            assertThat(ledgerEntry.type()).isEqualTo("grant");
                            assertThat(ledgerEntry.amountCredits()).isEqualTo(300);
                        })
                .anySatisfy(
                        ledgerEntry -> {
                            assertThat(ledgerEntry.type()).isEqualTo("topup");
                            assertThat(ledgerEntry.amountCredits()).isEqualTo(42);
                        });
    }

    @Test
    void authenticated_ledger_pages_with_cursor() {
        Seed seed = seedUser("billing-ledger-page");
        seedTopup(seed.tenantId(), 10, "FIRST");
        seedTopup(seed.tenantId(), 20, "SECOND");

        BillingLedgerHistoryResponse firstPage =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/credits/ledger?limit=1")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .body(BillingLedgerHistoryResponse.class);
        BillingLedgerHistoryResponse secondPage =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/api/credits/ledger")
                                                .queryParam("limit", 1)
                                                .queryParam("cursor", firstPage.nextCursor())
                                                .build())
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .body(BillingLedgerHistoryResponse.class);

        assertThat(firstPage.entries()).hasSize(1);
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.entries()).hasSize(1);
        assertThat(secondPage.entries().get(0).id()).isNotEqualTo(firstPage.entries().get(0).id());
    }

    @Test
    void authenticated_ledger_rejects_invalid_cursor() {
        Seed seed = seedUser("billing-ledger-invalid-cursor");

        ResponseEntity<String> response =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/credits/ledger?cursor=not-a-valid-cursor")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"code\":\"error.pagination.invalid_cursor\"");
    }

    @Test
    void authenticated_plans_do_not_include_checkout_url() {
        Seed seed = seedUser("billing-plans");

        ResponseEntity<String> response =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/plan-upgrades/plans")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"currentPlanCode\":\"FREE\"");
        assertThat(response.getBody()).doesNotContain("checkoutUrl");
    }

    @Test
    void authenticated_checkout_returns_url_after_plan_click() {
        Seed seed = seedUser("billing-checkout");
        BillingPlanEntity plusPlan = billingPlanRepository.findByCode("PLUS").orElseThrow();
        plusPlan.updateLemonSqueezyIds(99L, 123456L);
        billingPlanRepository.saveAndFlush(plusPlan);
        lemonSqueezyServer
                .expect(once(), requestTo("https://api.lemonsqueezy.com/v1/checkouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-lemon-api-key"))
                .andExpect(
                        content().contentType(MediaType.parseMediaType("application/vnd.api+json")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"123\"")))
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("\"id\":\"123456\"")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "\"email\":\"" + seed.email() + "\"")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "\"tenant_id\":\"" + seed.tenantId() + "\"")))
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("\"plan\":\"PLUS\"")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "\"credits\":\"2000\"")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "\"enabled_variants\":[123456]")))
                .andExpect(
                        content()
                                .string(org.hamcrest.Matchers.containsString("\"test_mode\":true")))
                .andRespond(
                        withSuccess(
                                """
                                        {
                                          "data": {
                                            "type": "checkouts",
                                            "id": "checkout-1",
                                            "attributes": {
                                              "url": "https://zeromail-test.lemonsqueezy.com/checkout/custom/checkout-1"
                                            }
                                          }
                                        }
                                        """,
                                MediaType.APPLICATION_JSON));

        BillingCheckoutResponse response =
                RestClient.create("http://localhost:" + port)
                        .post()
                        .uri("/api/plan-upgrades/plans/PLUS/checkout")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .body(BillingCheckoutResponse.class);
        BillingCheckoutResponse reusedResponse =
                RestClient.create("http://localhost:" + port)
                        .post()
                        .uri("/api/plan-upgrades/plans/PLUS/checkout")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .body(BillingCheckoutResponse.class);

        assertThat(response.checkoutUrl())
                .isEqualTo("https://zeromail-test.lemonsqueezy.com/checkout/custom/checkout-1");
        assertThat(reusedResponse.checkoutUrl()).isEqualTo(response.checkoutUrl());
        assertThat(
                        billingCheckoutSessionRepository.findByTenantIdOrderByCreatedAtDesc(
                                seed.tenantId()))
                .singleElement()
                .satisfies(
                        checkoutSession -> {
                            assertThat(checkoutSession.getTenantId()).isEqualTo(seed.tenantId());
                            assertThat(checkoutSession.getPlanCode()).isEqualTo("PLUS");
                            assertThat(checkoutSession.getUserEmail()).isEqualTo(seed.email());
                            assertThat(checkoutSession.getProviderCheckoutId())
                                    .isEqualTo("checkout-1");
                            assertThat(checkoutSession.getCheckoutUrl())
                                    .isEqualTo(
                                            "https://zeromail-test.lemonsqueezy.com/checkout/custom/checkout-1");
                            assertThat(checkoutSession.getStatus()).isEqualTo("CREATED");
                            assertThat(checkoutSession.getFailureReason()).isNull();
                            assertThat(checkoutSession.getReuseExpiresAt())
                                    .isAfter(checkoutSession.getCreatedAt());
                            assertThat(checkoutSession.getRequestJsonb())
                                    .contains("\"enabled_variants\": [123456]");
                            assertThat(checkoutSession.getResponseJsonb())
                                    .contains(
                                            "https://zeromail-test.lemonsqueezy.com/checkout/custom/checkout-1");
                        });
        lemonSqueezyServer.verify();
    }

    @Test
    void authenticated_checkout_rejects_lower_plan_when_higher_plan_still_active() {
        Seed seed = seedUser("billing-downgrade");
        BillingPlanEntity proPlan = billingPlanRepository.findByCode("PRO").orElseThrow();
        seedActivePlanPeriod(seed.tenantId(), proPlan, "TEST-PRO-" + seed.tenantId());

        ResponseEntity<String> response =
                RestClient.create("http://localhost:" + port)
                        .post()
                        .uri("/api/plan-upgrades/plans/PLUS/checkout")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody())
                .contains("\"code\":\"error.billing.plan.downgradeNotAllowed\"");
        assertThat(
                        billingCheckoutSessionRepository.findByTenantIdOrderByCreatedAtDesc(
                                seed.tenantId()))
                .isEmpty();
        lemonSqueezyServer.verify();
    }

    @Test
    void authenticated_checkout_persists_failed_session_when_provider_fails() {
        Seed seed = seedUser("billing-checkout-failure");
        BillingPlanEntity plusPlan = billingPlanRepository.findByCode("PLUS").orElseThrow();
        plusPlan.updateLemonSqueezyIds(99L, 123456L);
        billingPlanRepository.saveAndFlush(plusPlan);
        lemonSqueezyServer
                .expect(once(), requestTo("https://api.lemonsqueezy.com/v1/checkouts"))
                .andRespond(withBadRequest());

        ResponseEntity<String> response =
                noRetryRestClient()
                        .post()
                        .uri("/api/plan-upgrades/plans/PLUS/checkout")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(
                        billingCheckoutSessionRepository.findByTenantIdOrderByCreatedAtDesc(
                                seed.tenantId()))
                .singleElement()
                .satisfies(
                        checkoutSession -> {
                            assertThat(checkoutSession.getPlanCode()).isEqualTo("PLUS");
                            assertThat(checkoutSession.getStatus()).isEqualTo("FAILED");
                            assertThat(checkoutSession.getFailureReason()).isNotBlank();
                            assertThat(checkoutSession.getCheckoutUrl()).isNull();
                            assertThat(checkoutSession.getRequestJsonb())
                                    .contains("\"enabled_variants\": [123456]");
                        });
        lemonSqueezyServer.verify();
    }

    @Test
    void lemon_squeezy_webhook_processes_paid_order_event() {
        Seed seed = seedUser("billing-webhook");
        BillingPlanEntity plusPlan = billingPlanRepository.findByCode("PLUS").orElseThrow();
        plusPlan.updateLemonSqueezyIds(99L, 123456L);
        billingPlanRepository.saveAndFlush(plusPlan);
        String payload =
                """
                        {
                          "meta": {
                            "event_name": "order_created",
                            "custom_data": {
                              "tenant_id": "%s",
                              "plan": "PLUS"
                            }
                          },
                          "data": {
                            "type": "orders",
                            "id": "222001",
                            "attributes": {
                              "customer_id": 111,
                              "checkout_id": 333001,
                              "product_id": 99,
                              "variant_id": 123456,
                              "status": "paid",
                              "total": 199000,
                              "currency": "vnd",
                              "created_at": "2026-05-28T00:00:00.000000Z",
                              "customer": {
                                "email": "buyer@example.com"
                              },
                              "billing_address": {
                                "line1": "secret address"
                              },
                              "card_brand": "visa"
                            }
                          }
                        }
                        """
                        .formatted(seed.tenantId());
        String providerEventId = "event-order-created-1";

        ResponseEntity<Void> response =
                RestClient.create("http://localhost:" + port)
                        .post()
                        .uri("/api/plan-upgrades/webhooks/lemon-squeezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Event-Id", providerEventId)
                        .header("X-Signature", hmacSha256Hex("test-webhook-secret", payload))
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        BillingWebhookEventEntity webhookEvent =
                billingWebhookEventRepository.findByProviderEventId(providerEventId).orElseThrow();
        assertThat(webhookEvent.getEventName()).isEqualTo("order_created");
        assertThat(webhookEvent.getTenantId()).isEqualTo(seed.tenantId());
        assertThat(webhookEvent.getLemonSqueezyOrderId()).isEqualTo(222001L);
        assertThat(webhookEvent.isSignatureVerified()).isTrue();
        assertThat(webhookEvent.getProcessingStatus()).isEqualTo("PROCESSED");
        assertThat(webhookEvent.getPayloadJsonb()).contains("[redacted]");
        assertThat(webhookEvent.getPayloadJsonb())
                .doesNotContain("buyer@example.com")
                .doesNotContain("secret address")
                .doesNotContain("visa");
        BillingPlanPeriodEntity planPeriod =
                billingPlanPeriodRepository.findByProviderOrderId("222001").orElseThrow();
        assertThat(planPeriod.getTenantId()).isEqualTo(seed.tenantId());
        assertThat(planPeriod.getPlanId()).isEqualTo(plusPlan.getId());
        assertThat(planPeriod.getStatus()).isEqualTo("ACTIVE");
        assertThat(planPeriod.getProvider()).isEqualTo("LEMON_SQUEEZY");
        assertThat(planPeriod.getProviderCheckoutId()).isEqualTo("333001");
        assertThat(planPeriod.getProviderEventId()).isEqualTo(providerEventId);
        assertThat(planPeriod.getEffectiveAt())
                .isEqualTo(java.time.Instant.parse("2026-05-28T00:00:00Z"));
        assertThat(planPeriod.getExpiresAt())
                .isEqualTo(java.time.Instant.parse("2026-06-28T00:00:00Z"));
        assertThat(planPeriod.getPaidAt())
                .isEqualTo(java.time.Instant.parse("2026-05-28T00:00:00Z"));
        assertThat(planPeriod.getAmountVnd()).isEqualTo(199000L);
        assertThat(planPeriod.getCurrency()).isEqualTo("VND");
        assertThat(planPeriod.getLemonSqueezyCustomerId()).isEqualTo(111L);
        assertThat(planPeriod.getLemonSqueezyProductId()).isEqualTo(99L);
        assertThat(planPeriod.getLemonSqueezyVariantId()).isEqualTo(123456L);
        ResponseEntity<String> plansResponse =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/plan-upgrades/plans")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .toEntity(String.class);
        assertThat(plansResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(plansResponse.getBody()).contains("\"currentPlanCode\":\"PLUS\"");
    }

    @Test
    void lemon_squeezy_webhook_resets_order_credits_once_per_order() {
        Seed seed = seedUser("billing-webhook-credit");
        BillingPlanEntity plusPlan = billingPlanRepository.findByCode("PLUS").orElseThrow();
        plusPlan.updateLemonSqueezyIds(99L, 123456L);
        billingPlanRepository.saveAndFlush(plusPlan);
        String orderPayload =
                """
                        {
                          "meta": {
                            "event_name": "order_created",
                            "custom_data": {
                              "tenant_id": "%s",
                              "plan": "PLUS"
                            }
                          },
                          "data": {
                            "type": "orders",
                            "id": "222002",
                            "attributes": {
                              "customer_id": 111,
                              "checkout_id": 333002,
                              "product_id": 99,
                              "variant_id": 123456,
                              "user_email": "buyer@example.com",
                              "card_last_four": "4242",
                              "status": "paid",
                              "total": 199000,
                              "currency": "VND",
                              "created_at": "2026-05-28T00:00:00.000000Z"
                            }
                          }
                        }
                        """
                        .formatted(seed.tenantId());

        ResponseEntity<Void> orderResponse =
                postLemonSqueezyWebhook("event-order-created-credit-1", orderPayload);
        ResponseEntity<Void> duplicateOrderResponse =
                postLemonSqueezyWebhook("event-order-created-credit-duplicate", orderPayload);

        assertThat(orderResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(duplicateOrderResponse.getStatusCode().value()).isEqualTo(200);
        BillingWebhookEventEntity paymentEvent =
                billingWebhookEventRepository
                        .findByProviderEventId("event-order-created-credit-1")
                        .orElseThrow();
        assertThat(paymentEvent.getEventName()).isEqualTo("order_created");
        assertThat(paymentEvent.getTenantId()).isEqualTo(seed.tenantId());
        assertThat(paymentEvent.getLemonSqueezyOrderId()).isEqualTo(222002L);
        assertThat(paymentEvent.getProcessingStatus()).isEqualTo("PROCESSED");
        assertThat(paymentEvent.getPayloadJsonb())
                .doesNotContain("buyer@example.com")
                .doesNotContain("4242");
        assertThat(billingPlanPeriodRepository.findByProviderOrderId("222002")).isPresent();
        ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                .run(
                        () -> {
                            assertThat(
                                            creditGrantRepository
                                                    .findTenantGrantsByCategoryAndStatus(
                                                            seed.tenantId(),
                                                            CreditGrantCategory.MONTHLY_ALLOWANCE,
                                                            CreditGrantStatus.ACTIVE))
                                    .singleElement()
                                    .satisfies(
                                            creditGrant -> {
                                                assertThat(creditGrant.getAmountCredits())
                                                        .isEqualTo(2000);
                                                assertThat(creditGrant.getExpiresAt())
                                                        .isAfter(java.time.Instant.now());
                                            });
                            assertThat(
                                            creditLedgerEntryRepository
                                                    .sumAvailableCreditsForTenant(seed.tenantId()))
                                    .isEqualTo(2000);
                        });

        BillingBalanceResponse balanceResponse =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/credits/balance")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .body(BillingBalanceResponse.class);
        assertThat(balanceResponse.monthlyCredits()).isEqualTo(2000);
        assertThat(balanceResponse.additionalCredits()).isZero();
        assertThat(balanceResponse.monthlyCreditAllowance()).isEqualTo(2000);
    }

    @Test
    void lemon_squeezy_webhook_rejects_lower_plan_payment_when_higher_plan_still_active() {
        Seed seed = seedUser("billing-webhook-downgrade");
        BillingPlanEntity plusPlan = billingPlanRepository.findByCode("PLUS").orElseThrow();
        plusPlan.updateLemonSqueezyIds(99L, 123456L);
        billingPlanRepository.saveAndFlush(plusPlan);
        BillingPlanEntity proPlan = billingPlanRepository.findByCode("PRO").orElseThrow();
        seedActivePlanPeriod(seed.tenantId(), proPlan, "TEST-PRO-WEBHOOK-" + seed.tenantId());
        String orderPayload =
                """
                        {
                          "meta": {
                            "event_name": "order_created",
                            "custom_data": {
                              "tenant_id": "%s",
                              "plan": "PLUS"
                            }
                          },
                          "data": {
                            "type": "orders",
                            "id": "222003",
                            "attributes": {
                              "customer_id": 111,
                              "checkout_id": 333003,
                              "product_id": 99,
                              "variant_id": 123456,
                              "status": "paid",
                              "total": 199000,
                              "currency": "VND",
                              "created_at": "%s"
                            }
                          }
                        }
                        """
                        .formatted(seed.tenantId(), Instant.now());

        ResponseEntity<Void> response =
                postLemonSqueezyWebhook("event-order-created-downgrade", orderPayload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        BillingWebhookEventEntity webhookEvent =
                billingWebhookEventRepository
                        .findByProviderEventId("event-order-created-downgrade")
                        .orElseThrow();
        assertThat(webhookEvent.getProcessingStatus()).isEqualTo("FAILED");
        assertThat(webhookEvent.getProcessingError()).isEqualTo("plan_downgrade_not_allowed");
        assertThat(billingPlanPeriodRepository.findByProviderOrderId("222003")).isEmpty();
        assertThat(
                        billingPlanPeriodRepository.findCurrentTenantPlanPeriods(
                                seed.tenantId(), Instant.now()))
                .singleElement()
                .satisfies(
                        planPeriod -> {
                            assertThat(planPeriod.getPlanId()).isEqualTo(proPlan.getId());
                            assertThat(planPeriod.getStatus()).isEqualTo("ACTIVE");
                        });
    }

    @Test
    void lemon_squeezy_webhook_ignores_events_that_do_not_create_plan_periods() {
        String payload =
                """
                        {
                          "meta": {
                            "event_name": "subscription_created"
                          },
                          "data": {
                            "type": "subscriptions",
                            "id": "444",
                            "attributes": {
                              "user_email": "buyer@example.com"
                            }
                          }
                        }
                        """;
        String providerEventId = "event-subscription-created-ignored";

        ResponseEntity<Void> response = postLemonSqueezyWebhook(providerEventId, payload);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(billingWebhookEventRepository.findByProviderEventId(providerEventId)).isEmpty();
    }

    @Test
    void lemon_squeezy_webhook_rejects_invalid_signature() {
        String payload =
                """
                        {
                          "meta": {
                            "event_name": "subscription_created"
                          },
                          "data": {
                            "type": "subscriptions",
                            "id": "987654"
                          }
                        }
                        """;
        String providerEventId = "event-invalid-signature-1";

        ResponseEntity<Void> response =
                RestClient.create("http://localhost:" + port)
                        .post()
                        .uri("/api/plan-upgrades/webhooks/lemon-squeezy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Event-Id", providerEventId)
                        .header("X-Signature", "invalid")
                        .body(payload)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(billingWebhookEventRepository.findByProviderEventId(providerEventId)).isEmpty();
    }

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
        String googleSubject = "sub-" + label;
        String email = label + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                userRepository.save(
                                        new UserEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleSubject,
                                                email)));
        testSessionMinter.mint(googleSubject, email);
        return new Seed(tenantId, googleSubject, email);
    }

    private void seedTopup(UUID tenantId, int credits) {
        seedTopup(tenantId, credits, "DEFAULT");
    }

    private void seedTopup(UUID tenantId, int credits, String referenceSuffix) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                creditLedgerEntryRepository.saveAndFlush(
                                        CreditLedgerEntryEntity.topup(
                                                UUID.randomUUID(),
                                                tenantId,
                                                credits,
                                                "TEST-BALANCE-"
                                                        + referenceSuffix
                                                        + "-"
                                                        + tenantId)));
    }

    private void seedAdditionalGrant(
            UUID tenantId, CreditGrantCategory creditGrantCategory, int credits) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            UUID grantId = UUID.randomUUID();
                            String referenceId =
                                    "TEST-ADDITIONAL-"
                                            + creditGrantCategory.name()
                                            + "-"
                                            + tenantId;
                            creditGrantRepository.saveAndFlush(
                                    new CreditGrantEntity(
                                            grantId,
                                            tenantId,
                                            creditGrantCategory,
                                            CreditGrantStatus.ACTIVE,
                                            credits,
                                            java.time.Instant.now().minusSeconds(60),
                                            null,
                                            40,
                                            "TEST_ADDITIONAL",
                                            referenceId));
                            creditLedgerEntryRepository.saveAndFlush(
                                    CreditLedgerEntryEntity.grant(
                                            UUID.randomUUID(),
                                            tenantId,
                                            credits,
                                            grantId,
                                            "TEST_ADDITIONAL",
                                            referenceId));
                        });
    }

    private void seedActivePlanPeriod(
            UUID tenantId, BillingPlanEntity billingPlan, String orderId) {
        Instant effectiveAt = Instant.now().minusSeconds(60);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                billingPlanPeriodRepository.saveAndFlush(
                                        new BillingPlanPeriodEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                billingPlan.getId(),
                                                "ACTIVE",
                                                "ADMIN",
                                                orderId,
                                                null,
                                                null,
                                                effectiveAt,
                                                effectiveAt.plusSeconds(30L * 24 * 60 * 60),
                                                effectiveAt,
                                                0,
                                                "VND",
                                                null,
                                                billingPlan.getLemonSqueezyProductId(),
                                                billingPlan.getLemonSqueezyVariantId())));
    }

    private ResponseEntity<Void> postLemonSqueezyWebhook(String providerEventId, String payload) {
        return RestClient.create("http://localhost:" + port)
                .post()
                .uri("/api/plan-upgrades/webhooks/lemon-squeezy")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Event-Id", providerEventId)
                .header("X-Signature", hmacSha256Hex("test-webhook-secret", payload))
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private RestClient noRetryRestClient() {
        return RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    private String hmacSha256Hex(String signingSecret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception hmacFailure) {
            throw new IllegalStateException("Unable to sign webhook fixture", hmacFailure);
        }
    }

    private record Seed(UUID tenantId, String googleSubject, String email) {}
}
