package com.zeromail.api.controllers;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.persistence.TenantEntity;
import com.zeromail.core.persistence.TenantRepository;
import com.zeromail.core.persistence.UserEntity;
import com.zeromail.core.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * End-to-end coverage of the locale-resolution backend half (REQ-2 + REQ-3 backend slice).
 *
 * <p>Drives the live Spring MVC stack via MockMvc against the Testcontainers Postgres set
 * up by {@link ApiPostgresTestBase}, with the test auth shim {@link TestSessionSupport}
 * minting headers that populate both {@code SecurityContext} and the tenant
 * {@code ScopedValue}. Each test seeds its own tenant + user rows so they can run in any
 * order and never share state.
 *
 * <h2>Invariants asserted</h2>
 * <ol>
 *   <li>{@code GET /me} surfaces {@code preferredLanguage} reflecting the DB row
 *       (default {@code "vi"} per Plan 01 changeset 006).</li>
 *   <li>{@code PATCH /me/language} with {@code {"language":"en"}} returns 200,
 *       echoes the updated {@code MeResponse}, and persists the column so a follow-up
 *       {@code GET /me} sees the new value AND a direct JDBC SELECT confirms it.</li>
 *   <li>{@code PATCH /me/language} with {@code {"language":"zz"}} returns 400 with the
 *       Phase 1.1 ProblemDetail extension contract: {@code code == "error.validation"},
 *       {@code fieldErrors[0].field == "language"}, dotted code matches
 *       {@code error.validation.field.language.Pattern}. Body must NOT contain Java
 *       exception class names or framework package prefixes (D-C1 / T-1.1.04-04).</li>
 *   <li>{@code PATCH /me/language} is tenant-scoped — tenant A's PATCH leaves tenant B's
 *       {@code preferred_language} row unchanged (T-1.1.04-02).</li>
 * </ol>
 *
 * <p>Privacy: no raw email content, no LLM I/O, no refresh-token shapes appear in any
 * fixture. Sentinels use the obvious-marker convention (e.g., {@code "zz"} for the
 * disallowed locale).
 */
@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class MeLanguageIntegrationTest extends ApiPostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired TestSessionSupport.TestSessionMinter minter;
    @Autowired JdbcTemplate jdbc;

    private MockMvc mvc;

    private MockMvc mvc() {
        if (mvc == null) {
            mvc = MockMvcBuilders.webAppContextSetup(context).build();
        }
        return mvc;
    }

    private record Seed(UUID tenantId, UUID userId, String googleSubject, String email) {}

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, label));
        UUID userId = UUID.randomUUID();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
            users.save(new UserEntity(
                    userId, tenantId, "sub-" + label, label + "@example.com"));
        });
        minter.mint("sub-" + label, label + "@example.com");
        return new Seed(tenantId, userId, "sub-" + label, label + "@example.com");
    }

    @Test
    @DisplayName("GET /me returns preferredLanguage defaulting to 'vi'")
    void getMe_returnsPreferredLanguageDefaultVi() throws Exception {
        Seed s = seedUser("getme-default-vi");

        MvcResult res = mvc().perform(get("/me")
                        .header(TestSessionSupport.HEADER_SUBJECT, s.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, s.email()))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = new ObjectMapper().readTree(res.getResponse().getContentAsString());
        assertThat(json.path("preferredLanguage").asText()).isEqualTo("vi");
        assertThat(json.path("email").asText()).isEqualTo(s.email());
    }

    @Test
    @DisplayName("PATCH /me/language persists to DB and the next GET /me returns the new value")
    void patchMeLanguage_persistsToDb_andReturnsUpdated() throws Exception {
        Seed s = seedUser("patch-persists");

        MvcResult patchRes = mvc().perform(patch("/me/language")
                        .header(TestSessionSupport.HEADER_SUBJECT, s.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, s.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andReturn();

        assertThat(patchRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode patched = new ObjectMapper().readTree(patchRes.getResponse().getContentAsString());
        assertThat(patched.path("preferredLanguage").asText()).isEqualTo("en");

        MvcResult getRes = mvc().perform(get("/me")
                        .header(TestSessionSupport.HEADER_SUBJECT, s.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, s.email()))
                .andReturn();
        JsonNode reloaded = new ObjectMapper().readTree(getRes.getResponse().getContentAsString());
        assertThat(reloaded.path("preferredLanguage").asText()).isEqualTo("en");

        // Direct DB assertion — bypasses the application stack to prove the column was actually written.
        String dbValue = jdbc.queryForObject(
                "SELECT preferred_language FROM users WHERE id = ?",
                String.class,
                s.userId());
        assertThat(dbValue).isEqualTo("en");
    }

    @Test
    @DisplayName("PATCH /me/language with disallowed value returns 400 ProblemDetail with safe shape")
    void patchMeLanguage_invalidValue_returns400_with_validation_code_and_fieldError() throws Exception {
        Seed s = seedUser("patch-invalid");

        MvcResult res = mvc().perform(patch("/me/language")
                        .header(TestSessionSupport.HEADER_SUBJECT, s.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, s.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"zz\"}"))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(res.getResponse().getContentType()).startsWith("application/problem+json");

        String body = res.getResponse().getContentAsString();

        // Privacy / T-1.1.04-04: response must NOT echo Java exception class names or framework prefixes.
        assertThat(body)
                .as("ProblemDetail must not leak exception class names or framework package paths")
                .doesNotContain("ConstraintViolationException")
                .doesNotContain("MethodArgumentNotValidException")
                .doesNotContain("org.hibernate")
                .doesNotContain("org.springframework");

        JsonNode json = new ObjectMapper().readTree(body);
        assertThat(json.path("code").asText()).isEqualTo("error.validation");
        assertThat(json.path("message").asText()).isEqualTo("error.validation");
        assertThat(json.path("fieldErrors").isArray()).isTrue();
        assertThat(json.path("fieldErrors")).isNotEmpty();
        JsonNode firstFieldError = json.path("fieldErrors").get(0);
        assertThat(firstFieldError.path("field").asText()).isEqualTo("language");
        assertThat(firstFieldError.path("code").asText())
                .matches("error\\.validation\\.field\\.language\\..+");
        assertThat(firstFieldError.path("params").isObject()).isTrue();

        // DB row must be untouched after a rejected PATCH.
        String dbValue = jdbc.queryForObject(
                "SELECT preferred_language FROM users WHERE id = ?",
                String.class,
                s.userId());
        assertThat(dbValue).isEqualTo("vi");
    }

    @Test
    @DisplayName("PATCH /me/language is tenant-scoped — does not mutate other tenants' rows")
    void patchMeLanguage_isTenantScoped_doesNotMutateOtherTenants() throws Exception {
        Seed a = seedUser("tenant-a");
        Seed b = seedUser("tenant-b");

        // Authenticate as tenant A only and PATCH.
        MvcResult res = mvc().perform(patch("/me/language")
                        .header(TestSessionSupport.HEADER_SUBJECT, a.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, a.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        String aLang = jdbc.queryForObject(
                "SELECT preferred_language FROM users WHERE id = ?",
                String.class,
                a.userId());
        String bLang = jdbc.queryForObject(
                "SELECT preferred_language FROM users WHERE id = ?",
                String.class,
                b.userId());

        assertThat(aLang).as("tenant A's row was the target of the PATCH").isEqualTo("en");
        assertThat(bLang)
                .as("tenant B must remain unchanged — T-1.1.04-02 cross-tenant write attempt")
                .isEqualTo("vi");
    }
}
