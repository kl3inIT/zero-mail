package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class OnboardingCsrfIntegrationTest extends ApiPostgresTestBase {

    MockMvc mvc;
    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired OnboardingSelectionRepository selections;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void select_template_requires_csrf_token() throws Exception {
        Seed seed = seedTenant("csrf-reject");

        mvc.perform(
                        post("/api/onboarding/select-template")
                                .with(
                                        oidcLogin()
                                                .idToken(
                                                        token ->
                                                                token.claim(
                                                                                "sub",
                                                                                seed
                                                                                        .googleSubject())
                                                                        .claim(
                                                                                "email",
                                                                                seed.email())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"templateKey\":\"archive-receipts\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void select_template_accepts_spa_xsrf_cookie_and_header() throws Exception {
        Seed seed = seedTenant("csrf-accept");

        MockHttpServletResponse tokenResponse =
                mvc.perform(
                                get("/api/me")
                                        .with(
                                                oidcLogin()
                                                        .idToken(
                                                                token ->
                                                                        token.claim(
                                                                                        "sub",
                                                                                        seed
                                                                                                .googleSubject())
                                                                                .claim(
                                                                                        "email",
                                                                                        seed
                                                                                                .email()))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();

        Cookie xsrfCookie = tokenResponse.getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).as("SPA CSRF GET must issue XSRF-TOKEN cookie").isNotNull();

        mvc.perform(
                        post("/api/onboarding/select-template")
                                .with(
                                        oidcLogin()
                                                .idToken(
                                                        token ->
                                                                token.claim(
                                                                                "sub",
                                                                                seed
                                                                                        .googleSubject())
                                                                        .claim(
                                                                                "email",
                                                                                seed.email())))
                                .cookie(new MockCookie(xsrfCookie.getName(), xsrfCookie.getValue()))
                                .header("X-XSRF-TOKEN", xsrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"templateKey\":\"archive-receipts\"}"))
                .andExpect(status().isOk());

        ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                .run(
                        () ->
                                assertThat(selections.findByTenantId(seed.tenantId()))
                                        .singleElement()
                                        .satisfies(
                                                selection ->
                                                        assertThat(selection.getTemplateKey())
                                                                .isEqualTo("archive-receipts")));
    }

    private Seed seedTenant(String label) {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, label));
        var user =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        users.save(
                                                new UserEntity(
                                                        UUID.randomUUID(),
                                                        tenantId,
                                                        "sub-" + label,
                                                        label + "@example.com")));
        return new Seed(tenantId, user.getGoogleSubject(), user.getEmail());
    }

    private record Seed(UUID tenantId, String googleSubject, String email) {}
}
