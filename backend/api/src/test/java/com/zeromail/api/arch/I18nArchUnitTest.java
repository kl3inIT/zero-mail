package com.zeromail.api.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Phase 1.1 Plan 08 — final i18n / error-contract architectural barriers.
 *
 * <p>Locks four invariants at build time so future regressions fail compilation rather than
 * slipping past code review:
 *
 * <ol>
 *   <li><b>{@code noMessageSourceUsageInWebLayer}</b> — no class in {@code ..api..} depends on
 *       {@code org.springframework.context.MessageSource}. The server never constructs localized
 *       user-facing prose; the frontend localizes from stable dotted codes (CONTEXT.md §E reject;
 *       threat T-1.1.08-03; RESEARCH.md Common Pitfall 2 — server-built localized strings).
 *   <li><b>{@code globalExceptionHandlerExtendsResponseEntityExceptionHandler}</b> — defensive lock
 *       on the Plan 02 superclass invariant (mitigates Spring #35982 — without {@code
 *       ResponseEntityExceptionHandler}, the framework's default {@code
 *       MethodArgumentNotValidException} path silently bypasses our {@code @ExceptionHandler}
 *       advice under {@code spring.mvc.problemdetails.enabled=true}).
 *   <li><b>{@code noFieldErrorWithMessageStringField}</b> — no record/class in {@code
 *       ..api.error..} whose simple name contains {@code FieldError} declares a field named {@code
 *       message}. Locks D-C2 — server-built localized prose in field errors is forbidden; the
 *       frontend derives display text from the dotted {@code code}. (Reject from JHipster {@code
 *       FieldErrorVM}, which carries a String {@code message}.)
 *   <li><b>{@code noJdbcTemplateInAccountService}</b> — defensive lock against Pitfall 7: language
 *       updates and any other tenant-scoped account writes must go through JPA dirty-tracking on
 *       the managed {@code UserEntity} so Hibernate's {@code @TenantId} filter applies. Native
 *       UPDATEs via {@code JdbcTemplate} would bypass tenant isolation.
 * </ol>
 *
 * <p>This test lives under {@code backend/api/src/test} (not {@code backend/core}) because three of
 * the four rules target classes in {@code com.zeromail.api..}. ArchUnit only sees what is on the
 * test classpath — placing this test in {@code backend/core/src/test} would make every {@code
 * ..api..} predicate resolve vacuously (Plan 01 deviation Rule 3).
 */
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
public class I18nArchUnitTest {

    private static final String MESSAGE_SOURCE = "org.springframework.context.MessageSource";
    private static final String JDBC_TEMPLATE = "org.springframework.jdbc.core.JdbcTemplate";

    /**
     * No class anywhere under {@code com.zeromail.api..} (controllers, controller advice, filters,
     * configuration, services that escaped the package boundary, …) may depend on {@code
     * org.springframework.context.MessageSource}. Localized user-facing prose is frontend-owned
     * (CONTEXT.md §E reject "Server-side construction of localized user-facing prose"). Mitigates
     * threat T-1.1.08-03.
     */
    @ArchTest
    static final ArchRule noMessageSourceUsageInWebLayer =
            noClasses()
                    .that()
                    .resideInAPackage("com.zeromail.api..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(MESSAGE_SOURCE)
                    .because(
                            "CONTEXT.md §E reject + RESEARCH.md Pitfall 2: the server never "
                                    + "constructs localized user-facing strings; the frontend localizes "
                                    + "from stable dotted error codes.");

    /**
     * Defensive lock on the Plan 02 superclass invariant. Already enforced at runtime by {@code
     * ProblemDetailContractTest.globalExceptionHandler_extends_ResponseEntityExceptionHandler};
     * this rule fails the build at compile/import time if a future refactor breaks the inheritance
     * — preventing Spring #35982 / threat T-1.1.02-02 from re-emerging silently.
     */
    @ArchTest
    static final ArchRule globalExceptionHandlerExtendsResponseEntityExceptionHandler =
            classes()
                    .that()
                    .haveSimpleName("GlobalExceptionHandler")
                    .should()
                    .beAssignableTo(
                            org.springframework.web.servlet.mvc.method.annotation
                                    .ResponseEntityExceptionHandler.class)
                    .because(
                            "Plan 02 invariant + Spring issue #35982: without "
                                    + "ResponseEntityExceptionHandler as a superclass, the framework "
                                    + "bypasses our @ExceptionHandler advice for "
                                    + "MethodArgumentNotValidException under "
                                    + "spring.mvc.problemdetails.enabled=true.");

    /**
     * No record/class whose simple name contains {@code FieldError} (e.g., {@code FieldErrorDto},
     * hypothetical {@code FieldErrorVM}, {@code FieldErrorView}) may declare a field named {@code
     * message}. Locks the D-C2 shape {@code (field, code, params)} and explicitly rejects the
     * JHipster {@code FieldErrorVM} inheritance.
     *
     * <p>Implemented as a name-based check on the field declaration rather than a type-based check
     * because record components compile to {@code private final String} fields whose name is the
     * contract — a future writer who declared {@code String message} (server-built localized prose)
     * would trigger the rule regardless of whether they typed it as {@code String} or a wrapper.
     */
    @ArchTest
    static final ArchRule noFieldErrorWithMessageStringField =
            noClasses()
                    .that()
                    .resideInAPackage("com.zeromail.api.error..")
                    .and()
                    .haveSimpleNameContaining("FieldError")
                    .should(declareStringFieldNamed("message"))
                    .because(
                            "CONTEXT.md D-C2 + §E reject: FieldError-style records carry "
                                    + "(field, code, params) only — no server-built localized message string. "
                                    + "JHipster's FieldErrorVM.message is explicitly rejected.");

    /**
     * Custom condition: a class violates if it declares any field named {@code fieldName} whose raw
     * type is {@code java.lang.String}. Records compile their components to {@code private final
     * String <name>} fields, so this catches both classic POJOs and record components.
     */
    private static ArchCondition<JavaClass> declareStringFieldNamed(String fieldName) {
        return new ArchCondition<>("declare a String field named '" + fieldName + "'") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getFields()
                        .forEach(
                                field -> {
                                    if (!field.getName().equals(fieldName)) {
                                        return;
                                    }
                                    if (!field.getRawType().getName().equals("java.lang.String")) {
                                        return;
                                    }
                                    events.add(
                                            SimpleConditionEvent.violated(
                                                    field,
                                                    "Class "
                                                            + item.getName()
                                                            + " declares a String field named '"
                                                            + fieldName
                                                            + "' at "
                                                            + field.getSourceCodeLocation()
                                                            + " — Phase 1.1 D-C2 forbids server-built localized "
                                                            + "prose in field-error records (no JHipster FieldErrorVM "
                                                            + "shape)."));
                                });
            }
        };
    }

    /**
     * Defensive lock against Pitfall 7: tenant-scoped account writes must go through JPA
     * dirty-tracking on a managed {@code UserEntity} so Hibernate's {@code @TenantId} filter
     * applies. Native UPDATEs via {@code JdbcTemplate} bypass tenant isolation (cross-tenant write
     * risk T-1.1.04-02 / T-1.1.08-04). Plan 04 currently uses JPA dirty-tracking; this rule fails
     * the build if a future refactor reintroduces raw JDBC.
     */
    @ArchTest
    static final ArchRule noJdbcTemplateInAccountService =
            noClasses()
                    .that()
                    .haveSimpleName("AccountService")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(JDBC_TEMPLATE)
                    .because(
                            "Pitfall 7 + threat T-1.1.08-04: account writes go through JPA "
                                    + "dirty-tracking so the Hibernate @TenantId filter applies; raw "
                                    + "JDBC bypasses tenant isolation.");
}
