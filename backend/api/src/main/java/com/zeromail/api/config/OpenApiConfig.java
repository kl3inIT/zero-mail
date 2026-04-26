package com.zeromail.api.config;

import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * springdoc OpenAPI customization for Phase 1 + 1.1.
 *
 * <p>Two customizers are contributed:
 * <ol>
 *   <li>{@link #phase1Info()} — sets the API title, version, and description.</li>
 *   <li>{@link #apiErrorCustomizer()} — Plan 01.1-03: registers explicit
 *       {@code ApiError} and {@code FieldErrorDto} schemas in
 *       {@code components.schemas} and defaults every operation's 4xx/5xx
 *       responses to {@code application/problem+json} referencing
 *       {@code #/components/schemas/ApiError}.</li>
 * </ol>
 *
 * <p><b>Why a {@link GlobalOpenApiCustomizer} (not {@link OpenApiCustomizer}):</b>
 * future grouping via {@code GroupedOpenApi} would silently bypass plain
 * {@code OpenApiCustomizer} beans on the grouped paths. {@code GlobalOpenApiCustomizer}
 * applies to both the default doc and any group. (RESEARCH.md §"Common Pitfalls" — Pitfall
 * around grouped paths; springdoc faq.html.)
 *
 * <p><b>Why explicit schema registration:</b> {@code ProblemDetail.setProperty(...)}
 * extension members ({@code code}, {@code params}, {@code fieldErrors}, {@code message})
 * are dynamic — reflection-based schema generation never sees them, so
 * {@code openapi-typescript} regeneration produces an {@code ApiError} interface that
 * lacks the four members the frontend needs to switch on. The customizer registers the
 * full shape directly. (RESEARCH.md Pitfall 6.)
 *
 * <p><b>Title/detail policy:</b> {@code title} and {@code detail} stay populated as
 * generic English diagnostics for RFC 9457 compliance + operator log readability — the
 * frontend ignores them and switches on {@code code} (CONTEXT.md decision D-C1).
 */
@Configuration
public class OpenApiConfig {

    private static final String API_ERROR_SCHEMA = "ApiError";
    private static final String FIELD_ERROR_DTO_SCHEMA = "FieldErrorDto";
    private static final String API_ERROR_REF = "#/components/schemas/" + API_ERROR_SCHEMA;
    private static final String FIELD_ERROR_DTO_REF = "#/components/schemas/" + FIELD_ERROR_DTO_SCHEMA;
    private static final String PROBLEM_JSON = "application/problem+json";

    /**
     * Status codes whose response slots are auto-populated with the {@code ApiError}
     * default when not already declared on the operation. 500 is included because
     * unexpected server faults must still serialize through the same wire shape so the
     * FE error helper can render a localized fallback.
     */
    private static final List<Integer> DEFAULT_ERROR_STATUSES =
            List.of(400, 401, 403, 404, 409, 500);

    @Bean
    OpenApiCustomizer phase1Info() {
        return api -> api.setInfo(new Info()
                .title("Zero Mail API")
                .version("0.1.1")
                .description("Phase 1 skeleton + Phase 1.1 i18n/error contract"));
    }

    @Bean
    GlobalOpenApiCustomizer apiErrorCustomizer() {
        return openApi -> {
            // WR-04: scalar union schema for `params` map values. The backend's
            // AllowedParamScalars filter only emits String/Number/Boolean (it drops
            // anything else before it reaches the wire), so the wire shape is
            // `{ [k: string]: string | number | boolean }` — not the previous
            // `{ [k: string]: object }` which openapi-typescript materialized as
            // `Record<string, never>` and broke generated-client safety for ICU
            // parameter use. Cite: AllowedParamScalars, REVIEW.md WR-04.
            Schema<?> safeParamScalar = new Schema<>()
                    .oneOf(List.of(
                            new StringSchema(),
                            new IntegerSchema(),
                            new NumberSchema(),
                            new BooleanSchema()))
                    .description("Allow-listed scalar value: string, integer, number, or boolean. "
                            + "Filtered through AllowedParamScalars before serialization.");

            // --- 1. Register FieldErrorDto schema (referenced by ApiError.fieldErrors[]) ---
            Schema<?> fieldErrorDto = new ObjectSchema()
                    .description("Per-field validation error. CONTEXT.md decision D-C2: "
                            + "{ field, code, params }. The JHipster `message` and `objectName` "
                            + "fields are deliberately rejected — the frontend localizes the "
                            + "dotted `code` via the errors.field.* dictionary.")
                    .addProperty("field", new StringSchema()
                            .description("Dotted path to the offending field, e.g. "
                                    + "`email`, `templates[0].key`."))
                    .addProperty("code", new StringSchema()
                            .description("Dotted hierarchical key inside the errors.field.* namespace."))
                    .addProperty("params", new MapSchema()
                            .additionalProperties(safeParamScalar)
                            .description("Allow-listed scalars (numbers, booleans, dotted keys, "
                                    + "short resource identifiers). Never raw user content."))
                    .required(List.of("field", "code"));

            // --- 2. Register ApiError schema (the application/problem+json wire shape) ---
            Schema<?> apiError = new ObjectSchema()
                    .description("RFC 9457 ProblemDetail extended with code/params/fieldErrors. "
                            + "Wire shape produced by Spring 7 ProblemDetail.setProperty(...) "
                            + "flattened by Jackson 3 ProblemDetailJacksonMixin. "
                            + "Phase 1.1 introduces this contract; the FE switches on `code`.")
                    .addProperty("type", new StringSchema()
                            .format("uri")
                            .description("RFC 9457 problem type URI; defaults to `about:blank`."))
                    .addProperty("title", new StringSchema()
                            .description("Generic English diagnostic. NOT the user-facing message — FE ignores it."))
                    .addProperty("status", new IntegerSchema()
                            .format("int32")
                            .description("HTTP status code echoed in the body."))
                    .addProperty("detail", new StringSchema()
                            .description("Generic English diagnostic. NOT the user-facing message — FE ignores it."))
                    .addProperty("instance", new StringSchema()
                            .format("uri")
                            .description("Optional URI identifying this specific occurrence."))
                    .addProperty("code", new StringSchema()
                            .description("Stable dotted hierarchical key (e.g. `error.auth.unauthorized`). "
                                    + "Frontend localizes via the errors.* dictionary. "
                                    + "ALWAYS PRESENT."))
                    .addProperty("params", new MapSchema()
                            .additionalProperties(safeParamScalar)
                            .description("Allow-listed ICU placeholder scalars for the FE error message. "
                                    + "Filtered through AllowedParamScalars; never carries raw user content, "
                                    + "Gmail body fragments, SQL constraint names, or exception class names."))
                    .addProperty("fieldErrors", new ArraySchema()
                            .items(new Schema<>().$ref(FIELD_ERROR_DTO_REF))
                            .description("Per-field validation errors. Present only on validation failures."))
                    .addProperty("message", new StringSchema()
                            .deprecated(true)
                            .description("Transitional alias of `code`, kept for one phase per CONTEXT.md "
                                    + "decision D-C1. Prefer `code`."))
                    .required(List.of("code"));

            if (openApi.getComponents() == null) {
                openApi.components(new io.swagger.v3.oas.models.Components());
            }
            openApi.getComponents().addSchemas(API_ERROR_SCHEMA, apiError);
            openApi.getComponents().addSchemas(FIELD_ERROR_DTO_SCHEMA, fieldErrorDto);

            // --- 3. Default 4xx/5xx responses on every operation ---
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(op -> {
                        ApiResponses responses = op.getResponses();
                        if (responses == null) {
                            responses = new ApiResponses();
                            op.setResponses(responses);
                        }
                        Set<String> existing = Set.copyOf(responses.keySet());
                        for (Integer status : DEFAULT_ERROR_STATUSES) {
                            String key = String.valueOf(status);
                            if (existing.contains(key)) {
                                // Operation explicitly declares this status — do not overwrite.
                                continue;
                            }
                            responses.addApiResponse(key, new ApiResponse()
                                    .description(reasonPhrase(status))
                                    .content(new Content().addMediaType(PROBLEM_JSON,
                                            new MediaType().schema(new Schema<>().$ref(API_ERROR_REF)))));
                        }
                    }));
        };
    }

    private static String reasonPhrase(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }
}
