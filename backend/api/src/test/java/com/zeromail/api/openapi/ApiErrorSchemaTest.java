package com.zeromail.api.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Snapshot test for Plan 01.1-03 — asserts the springdoc {@code GlobalOpenApiCustomizer}
 * registers a reusable {@code ApiError} schema (with the four extension members added by
 * {@code ProblemDetail.setProperty(...)} on the wire, which would otherwise be invisible to
 * reflection-based schema generation; see RESEARCH.md Pitfall 6) and defaults every Phase 1
 * operation's 4xx/5xx responses to {@code application/problem+json} referencing it.
 *
 * <p>Boots the full Spring context once, hits {@code /v3/api-docs}, and parses the JSON.
 * No reflective programmatic invocation of the customizer — we want the assertions to
 * exercise the same artifact the {@code openapi-typescript} codegen will consume.
 */
@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class ApiErrorSchemaTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void api_docs_contains_ApiError_schema_with_extension_members() throws Exception {
        JsonNode root = fetchApiDocs();

        JsonNode apiError = root.path("components").path("schemas").path("ApiError");
        assertThat(apiError.isMissingNode()).as("ApiError schema must be registered").isFalse();

        JsonNode props = apiError.path("properties");
        assertThat(props.path("code").path("type").asText()).isEqualTo("string");
        assertThat(props.path("title").path("type").asText()).isEqualTo("string");
        assertThat(props.path("status").path("type").asText()).isEqualTo("integer");
        assertThat(props.path("detail").path("type").asText()).isEqualTo("string");
        assertThat(props.path("type").path("type").asText()).isEqualTo("string");
        assertThat(props.has("params")).as("params extension member must be schema-registered").isTrue();
        assertThat(props.has("fieldErrors")).as("fieldErrors extension member must be schema-registered").isTrue();
        assertThat(props.has("message")).as("transitional message alias must be schema-registered").isTrue();

        JsonNode required = apiError.path("required");
        assertThat(required.isArray()).isTrue();
        boolean hasCode = false;
        for (JsonNode r : required) {
            if ("code".equals(r.asText())) hasCode = true;
        }
        assertThat(hasCode).as("ApiError.required must contain 'code'").isTrue();

        // fieldErrors should reference FieldErrorDto via items.$ref
        JsonNode fieldErrors = props.path("fieldErrors");
        assertThat(fieldErrors.path("type").asText()).isEqualTo("array");
        String itemsRef = fieldErrors.path("items").path("$ref").asText();
        assertThat(itemsRef).isEqualTo("#/components/schemas/FieldErrorDto");
    }

    @Test
    void api_docs_contains_FieldErrorDto_schema_without_message() throws Exception {
        JsonNode root = fetchApiDocs();
        JsonNode dto = root.path("components").path("schemas").path("FieldErrorDto");
        assertThat(dto.isMissingNode()).as("FieldErrorDto schema must be registered").isFalse();

        JsonNode props = dto.path("properties");
        assertThat(props.path("field").path("type").asText()).isEqualTo("string");
        assertThat(props.path("code").path("type").asText()).isEqualTo("string");
        assertThat(props.has("params")).isTrue();
        // CONTEXT.md §E reject — FieldErrorDto MUST NOT carry a message string (JHipster anti-pattern).
        assertThat(props.has("message")).as("FieldErrorDto must not carry server-built message string").isFalse();

        JsonNode required = dto.path("required");
        boolean hasField = false;
        boolean hasCode = false;
        for (JsonNode r : required) {
            if ("field".equals(r.asText())) hasField = true;
            if ("code".equals(r.asText())) hasCode = true;
        }
        assertThat(hasField).as("FieldErrorDto.required must contain 'field'").isTrue();
        assertThat(hasCode).as("FieldErrorDto.required must contain 'code'").isTrue();
    }

    @Test
    void me_get_response_401_uses_problem_json_referencing_ApiError() throws Exception {
        JsonNode root = fetchApiDocs();
        JsonNode meGet = root.path("paths").path("/me").path("get");
        assertThat(meGet.isMissingNode()).as("/me GET operation must be present").isFalse();

        JsonNode r401 = meGet.path("responses").path("401");
        assertThat(r401.isMissingNode()).as("/me GET 401 response must be defaulted by the customizer").isFalse();

        String ref = r401.path("content").path("application/problem+json").path("schema").path("$ref").asText();
        assertThat(ref).isEqualTo("#/components/schemas/ApiError");
    }

    @Test
    void info_version_is_bumped_to_0_1_1() throws Exception {
        JsonNode root = fetchApiDocs();
        assertThat(root.path("info").path("version").asText()).isEqualTo("0.1.1");
    }

    /**
     * WR-04: ApiError.params (and FieldErrorDto.params) additionalProperties must
     * be a oneOf of scalar primitives — string, integer, number, boolean — not the
     * previous {@code object} schema. Without this, openapi-typescript materializes
     * the params map values as {@code Record<string, never>}, which makes the
     * generated client unable to construct any concrete params payload and causes
     * the documented contract to diverge from the wire shape produced by
     * AllowedParamScalars.
     */
    @Test
    void api_error_params_additional_properties_are_scalar_union() throws Exception {
        JsonNode root = fetchApiDocs();
        JsonNode apiError = root.path("components").path("schemas").path("ApiError");
        JsonNode addl = apiError.path("properties").path("params").path("additionalProperties");
        assertScalarUnion(addl, "ApiError.params");

        JsonNode fieldErrorDto = root.path("components").path("schemas").path("FieldErrorDto");
        JsonNode fieldAddl = fieldErrorDto.path("properties").path("params").path("additionalProperties");
        assertScalarUnion(fieldAddl, "FieldErrorDto.params");
    }

    private void assertScalarUnion(JsonNode addl, String label) {
        assertThat(addl.isMissingNode())
                .as("%s.additionalProperties must be present", label)
                .isFalse();
        JsonNode oneOf = addl.path("oneOf");
        assertThat(oneOf.isArray())
                .as("%s.additionalProperties.oneOf must be an array of scalar primitives", label)
                .isTrue();
        java.util.Set<String> types = new java.util.HashSet<>();
        for (JsonNode opt : oneOf) {
            types.add(opt.path("type").asText());
        }
        assertThat(types)
                .as("%s.additionalProperties.oneOf must include string, integer, number, boolean", label)
                .contains("string", "integer", "number", "boolean");
        assertThat(types)
                .as("%s.additionalProperties.oneOf must NOT include 'object' (the broken pre-WR-04 shape)", label)
                .doesNotContain("object");
    }

    private JsonNode fetchApiDocs() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);
        String body = client.get().uri("/v3/api-docs").retrieve().body(String.class);
        return new ObjectMapper().readTree(body);
    }
}
