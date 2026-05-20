package com.zeromail.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class AdminChainCookieIsolationTest {

    private static final Path SECURITY_CONFIG =
            Path.of("src/main/java/com/zeromail/api/security/SecurityConfig.java");
    private static final Path OPEN_API_CONFIG =
            Path.of("src/main/java/com/zeromail/api/config/OpenApiConfig.java");
    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");
    private static final Path ENROLLMENT_SESSION_CONTROLLER =
            Path.of("src/main/java/com/zeromail/api/security/EnrollmentSessionController.java");
    private static final Path ADMIN_BOOTSTRAP_RUNNER =
            Path.of("src/main/java/com/zeromail/api/admin/AdminBootstrapRunner.java");
    private static final Path DOCS_FREEZE =
            Path.of("../../docs/ops/admin-interface-freeze.md").normalize();

    @Test
    void admin_chain_has_own_cookie_and_never_uses_spa_enroll_path() throws IOException {
        String securityConfig = Files.readString(SECURITY_CONFIG);

        assertThat(securityConfig).contains("SecurityFilterChain adminChain");
        assertThat(securityConfig).contains("@Order(1)");
        assertThat(securityConfig)
                .contains("\"/api/admin/**\"")
                .contains("\"/webauthn/**\"")
                .contains("\"/login/webauthn/**\"");
        assertThat(securityConfig).doesNotContain("EnrollmentTokenGate");
        assertThat(securityConfig).doesNotContain("\"/enroll\"");
        assertThat(securityConfig).contains("SESSION_ADMIN").contains("SESSION_USER");
    }

    @Test
    void enrollment_session_rest_controller_replaces_legacy_enrollment_gate() throws IOException {
        assertThat(Files.exists(ENROLLMENT_SESSION_CONTROLLER)).isTrue();
        String enrollmentSessionController = Files.readString(ENROLLMENT_SESSION_CONTROLLER);

        assertThat(enrollmentSessionController)
                .contains("@PostMapping(\"/api/admin/enrollment/session\")")
                .contains("EnrollmentTokenService")
                .contains("HttpStatus.GONE");
        assertThat(allProductionSource()).doesNotContain("EnrollmentTokenGate");
    }

    @Test
    void bootstrap_runner_prints_enrollment_url_only_to_stdout() throws IOException {
        assertThat(Files.exists(ADMIN_BOOTSTRAP_RUNNER)).isTrue();
        String adminBootstrapRunner = Files.readString(ADMIN_BOOTSTRAP_RUNNER);

        assertThat(adminBootstrapRunner)
                .contains("implements CommandLineRunner")
                .contains("System.out.println")
                .contains("https://admin.zeromail.com/enroll?token=");
        assertThat(adminBootstrapRunner).doesNotContain("LoggerFactory");
    }

    @Test
    void grouped_openapi_and_admin_properties_are_declared() throws IOException {
        String openApiConfig = Files.readString(OPEN_API_CONFIG);
        String applicationYml = Files.readString(APPLICATION_YML);

        assertThat(openApiConfig)
                .contains("GroupedOpenApi")
                .contains(".group(\"public\")")
                .contains(".pathsToExclude(\"/api/admin/**\")")
                .contains(".group(\"admin\")")
                .contains(".pathsToMatch(\"/api/admin/**\")");
        assertThat(applicationYml)
                .contains("bootstrap-emails")
                .contains("hmac-kek-base64")
                .contains("ZEROMAIL_ADMIN_AUDIT_HMAC_KEK_BASE64");
    }

    @Test
    void interface_freeze_document_pins_review_addendum_contract() throws IOException {
        assertThat(Files.exists(DOCS_FREEZE)).isTrue();
        String interfaceFreeze = Files.readString(DOCS_FREEZE);

        assertThat(interfaceFreeze)
                .contains("last_verified")
                .contains("POST /api/admin/enrollment/session")
                .contains("SESSION_ADMIN")
                .contains("SESSION_USER")
                .contains("spring:session:admin")
                .contains("GroupedOpenApi");
    }

    private static String allProductionSource() throws IOException {
        try (Stream<Path> productionFiles = Files.walk(Path.of("src/main/java"))) {
            StringBuilder source = new StringBuilder();
            for (Path productionFile : productionFiles.filter(Files::isRegularFile).toList()) {
                source.append(Files.readString(productionFile)).append('\n');
            }
            return source.toString();
        }
    }
}
