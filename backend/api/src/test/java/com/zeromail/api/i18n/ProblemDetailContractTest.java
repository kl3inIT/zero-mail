package com.zeromail.api.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.zeromail.api.config.GlobalExceptionHandler;
import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Locks the wire shape of the Phase 1.1 ProblemDetail extension contract (CONTEXT.md
 * decisions D-C1, D-C2, D-C3) and the supporting class hierarchy invariant
 * (RESEARCH.md Pitfall 1).
 *
 * <p>Exercises three independent invariants:
 * <ol>
 *   <li>Validation errors flow through {@code handleMethodArgumentNotValid} and emit
 *       {@code application/problem+json} carrying top-level {@code code = "error.validation"}
 *       plus a {@code fieldErrors[]} array shaped {@code {field, code, params}}.</li>
 *   <li>{@link GlobalExceptionHandler} extends Spring's
 *       {@link org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler},
 *       which is mandatory for Bean Validation handlers to fire under
 *       {@code spring.mvc.problemdetails.enabled=true} (mitigates threat T-1.1.02-02).</li>
 *   <li>Existing Phase 1 handlers emit dotted D-C3 codes (e.g.,
 *       {@code error.auth.forbidden}) instead of the legacy flat keys.</li>
 * </ol>
 *
 * <p>Privacy invariant: the validation test seeds a sentinel value
 * ({@code <<SENTINEL_USER_INPUT>>}) into the rejected field and asserts the sentinel
 * never appears in the response body — the {@link com.zeromail.api.error.AllowedParamScalars}
 * filter must drop it from {@code params}, and the handler must not leak it through
 * {@code title} / {@code detail}.
 */
@ActiveProfiles("test")
@Import({TestSessionSupport.class, ProblemDetailContractTest.TestFixtureConfig.class})
class ProblemDetailContractTest extends ApiPostgresTestBase {

    @Autowired WebApplicationContext context;

    private MockMvc mvc;

    private MockMvc mvc() {
        if (mvc == null) {
            mvc = MockMvcBuilders.webAppContextSetup(context).build();
        }
        return mvc;
    }

    @Test
    @DisplayName("validation error returns problem+json with top-level code and fieldErrors")
    void validationError_returns_problemJson_with_top_level_code_and_fieldErrors() throws Exception {
        // Sentinel must never appear in the response — AllowedParamScalars filters it.
        String body = "{\"language\":\"<<SENTINEL_USER_INPUT>>\"}";

        MvcResult res = mvc().perform(post("/test/validate-locale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(400);
        assertThat(res.getResponse().getContentType()).startsWith("application/problem+json");

        String responseBody = res.getResponse().getContentAsString();
        assertThat(responseBody)
                .as("AllowedParamScalars must filter the sentinel out of params; "
                        + "title/detail must remain generic English diagnostics")
                .doesNotContain("<<SENTINEL_USER_INPUT>>");

        JsonNode json = new ObjectMapper().readTree(responseBody);
        assertThat(json.path("code").asString()).isEqualTo("error.validation");
        assertThat(json.path("message").asString()).isEqualTo("error.validation"); // transitional alias
        assertThat(json.path("fieldErrors")).isNotEmpty();
        JsonNode firstFieldError = json.path("fieldErrors").get(0);
        assertThat(firstFieldError.path("field").asString()).isEqualTo("language");
        assertThat(firstFieldError.path("code").asString())
                .matches("error\\.validation\\.field\\.language\\..+");
        assertThat(firstFieldError.path("params").isObject()).isTrue();
    }

    @Test
    @DisplayName("GlobalExceptionHandler extends ResponseEntityExceptionHandler (Pitfall 1)")
    void globalExceptionHandler_extends_ResponseEntityExceptionHandler() throws Exception {
        Class<?> superClass = Class.forName("com.zeromail.api.config.GlobalExceptionHandler")
                .getSuperclass();

        assertThat(superClass.getName())
                .as("Mitigates threat T-1.1.02-02 / Spring issue #35982 — without this superclass, "
                        + "MethodArgumentNotValidException is delivered to the framework default "
                        + "handler and never reaches our advice under "
                        + "spring.mvc.problemdetails.enabled=true.")
                .isEqualTo("org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler");
    }

    @Test
    @DisplayName("existing handlers carry D-C3 dotted codes (error.auth.forbidden)")
    void existingHandlersCarryDottedCodes() throws Exception {
        MvcResult res = mvc().perform(get("/test/throw-access-denied")).andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(403);
        assertThat(res.getResponse().getContentType()).startsWith("application/problem+json");

        JsonNode json = new ObjectMapper().readTree(res.getResponse().getContentAsString());
        assertThat(json.path("code").asString()).isEqualTo("error.auth.forbidden");
        assertThat(json.path("message").asString()).isEqualTo("error.auth.forbidden");
        assertThat(json.path("params").isObject()).isTrue();
    }

    /**
     * Test-fixture configuration: contributes two endpoints the test drives.
     * <ul>
     *   <li>{@code POST /test/validate-locale} — bound to {@link TestLocaleBody}, fires
     *       {@code MethodArgumentNotValidException} when {@code language} fails the
     *       {@code vi|en} pattern.</li>
     *   <li>{@code GET /test/throw-access-denied} — throws {@link AccessDeniedException}
     *       so the existing {@code AccessDeniedException} handler runs end-to-end.</li>
     * </ul>
     * Decoupled from Plan 04's {@code PATCH /me/language} so this test does not depend
     * on a downstream plan being landed.
     */
    @TestConfiguration
    static class TestFixtureConfig {
        @org.springframework.context.annotation.Bean
        TestFixtureController testFixtureController() {
            return new TestFixtureController();
        }
    }

    @RestController
    static class TestFixtureController {
        @PostMapping("/test/validate-locale")
        public void validateLocale(@Valid @RequestBody TestLocaleBody body) {
            // Body is valid by the time we get here — the handler under test fires earlier.
        }

        @GetMapping("/test/throw-access-denied")
        public void throwAccessDenied() {
            throw new AccessDeniedException("test-fixture forbidden");
        }
    }

    public record TestLocaleBody(@Pattern(regexp = "vi|en") String language) {}
}
