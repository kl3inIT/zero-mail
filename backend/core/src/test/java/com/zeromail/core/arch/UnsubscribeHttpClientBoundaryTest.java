package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

/**
 * UNS-08a — Only {@link com.zeromail.core.cleanup.usecases.UnsubscribeHttpClient} may construct or
 * obtain a {@link java.net.http.HttpClient} or Spring {@code RestClient} inside the cleanup module.
 * This is the architectural guardrail behind the security boundary: every outbound unsubscribe POST
 * must flow through the one centralized, audited HTTP client so that HTTPS-only validation,
 * redirect-policy=NEVER, timeout limits, and provenance check against the persisted {@code
 * list_unsubscribe_url} can be enforced in one place.
 *
 * <p>Wave 0 RED expectation: this rule fails to load until {@code
 * com.zeromail.core.cleanup.usecases.UnsubscribeHttpClient} is shipped (Wave 3 / Plan 05) —
 * ArchUnit can still emit empty-should results, but the rule narrative wants the production class
 * to exist so the allow-list reference is meaningful.
 */
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class UnsubscribeHttpClientBoundaryTest {

    private static final String UNSUBSCRIBE_HTTP_CLIENT =
            "com.zeromail.core.cleanup.usecases.UnsubscribeHttpClient";
    private static final String JDK_HTTP_CLIENT_OWNER = "java.net.http.HttpClient";
    private static final String JDK_HTTP_CLIENT_BUILDER_OWNER = "java.net.http.HttpClient.Builder";
    private static final String SPRING_REST_CLIENT_OWNER =
            "org.springframework.web.client.RestClient";

    @Test
    void unsubscribeHttpClient_classMustExist() {
        assertThatCode(() -> Class.forName(UNSUBSCRIBE_HTTP_CLIENT))
                .as(
                        "UNS-08: the allow-listed HTTP client class must exist so the ArchUnit"
                                + " allow-list reference is meaningful — Wave 3 / Plan 05 ships it")
                .doesNotThrowAnyException();
    }

    @ArchTest
    static final ArchRule only_unsubscribe_http_client_creates_http_machinery =
            classes()
                    .that()
                    .resideInAPackage("..core.cleanup..")
                    .should(
                            new ArchCondition<JavaClass>(
                                    "create HTTP client machinery only from "
                                            + UNSUBSCRIBE_HTTP_CLIENT) {
                                @Override
                                public void check(
                                        JavaClass javaClass, ConditionEvents conditionEvents) {
                                    if (javaClass.getName().equals(UNSUBSCRIBE_HTTP_CLIENT)) {
                                        return;
                                    }
                                    javaClass
                                            .getMethodCallsFromSelf()
                                            .forEach(
                                                    methodCall -> {
                                                        if (!isHttpClientCreation(
                                                                methodCall
                                                                        .getTargetOwner()
                                                                        .getName(),
                                                                methodCall.getName())) {
                                                            return;
                                                        }
                                                        conditionEvents.add(
                                                                SimpleConditionEvent.violated(
                                                                        methodCall,
                                                                        "Only "
                                                                                + UNSUBSCRIBE_HTTP_CLIENT
                                                                                + " may construct HttpClient/RestClient; found "
                                                                                + methodCall
                                                                                        .getSourceCodeLocation()));
                                                    });
                                }
                            })
                    .because(
                            "UNS-08: HTTP unsubscribe traffic is centralized behind"
                                    + " UnsubscribeHttpClient (HTTPS-only, no redirect, persisted"
                                    + " URL provenance).")
                    .allowEmptyShould(true);

    private static boolean isHttpClientCreation(String targetOwnerName, String methodName) {
        String normalizedOwnerName = targetOwnerName.replace('$', '.');
        boolean jdkHttpClientStaticFactory =
                normalizedOwnerName.equals(JDK_HTTP_CLIENT_OWNER)
                        && (methodName.equals("newHttpClient") || methodName.equals("newBuilder"));
        boolean jdkHttpClientBuilderBuild =
                normalizedOwnerName.equals(JDK_HTTP_CLIENT_BUILDER_OWNER)
                        && methodName.equals("build");
        boolean springRestClientFactory =
                normalizedOwnerName.equals(SPRING_REST_CLIENT_OWNER)
                        && (methodName.equals("create") || methodName.equals("builder"));
        return jdkHttpClientStaticFactory || jdkHttpClientBuilderBuild || springRestClientFactory;
    }
}
