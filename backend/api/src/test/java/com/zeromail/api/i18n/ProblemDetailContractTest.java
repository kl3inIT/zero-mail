package com.zeromail.api.i18n;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Wave 0 RED stub — Plan 02 turns this GREEN.
 *
 * Asserts the locked ProblemDetail extension contract (CONTEXT.md decision D-C1, D-C2):
 *  - validation errors return application/problem+json with $.code == "error.validation"
 *    and $.fieldErrors[*] entries shaped { field, code, params? }
 *  - GlobalExceptionHandler extends Spring's ResponseEntityExceptionHandler, which is
 *    mandatory to receive MethodArgumentNotValidException callbacks (RESEARCH.md
 *    Pitfall 1).
 *
 * Test methods are stubs only; full @SpringBootTest @AutoConfigureMockMvc wiring is
 * added by Plan 02 once the upgraded handler exists. Class-level @Disabled keeps the
 * suite green.
 */
@Disabled("Wave 0 RED stub — implemented by Plan 02")
class ProblemDetailContractTest {

    @Test
    void validationError_returns_problemJson_with_top_level_code_and_fieldErrors() {
        // Plan 02: POST invalid body to /me/language with { "language": "zz" }
        // Expect 400, Content-Type: application/problem+json,
        //   $.code == "error.validation"
        //   $.fieldErrors[0].field == "language"
        //   $.fieldErrors[0].code starts with "error.validation.field.language."
        // Sentinel-driven assertions only — no PII / email body / SQL constraint names.
    }

    @Test
    void globalExceptionHandler_extends_ResponseEntityExceptionHandler() {
        // Plan 02: reflection assert
        //   Class<?> superClass = Class.forName("com.zeromail.api.config.GlobalExceptionHandler")
        //       .getSuperclass();
        //   assertThat(superClass.getName())
        //       .isEqualTo("org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler");
        // Cite: RESEARCH.md Pitfall 1 — without this superclass, MethodArgumentNotValidException
        // is delivered to the framework default handler and never reaches our advice.
    }
}
