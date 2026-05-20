package com.zeromail.api.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminMasterKeyControllerContractTest {

    private static final Path CONTROLLER =
            Path.of(
                    "src/main/java/com/zeromail/api/controllers/admin/AdminMasterKeyController.java");
    private static final Path SET_REQUEST =
            Path.of("src/main/java/com/zeromail/api/dto/admin/mkey/MasterKeySetRequest.java");
    private static final Path ROTATE_REQUEST =
            Path.of("src/main/java/com/zeromail/api/dto/admin/mkey/RotateMasterKeyRequest.java");
    private static final Path TEST_REQUEST =
            Path.of("src/main/java/com/zeromail/api/dto/admin/mkey/TestConnectionRequest.java");
    private static final Path ADMIN_REQUEST_BODY =
            Path.of("src/main/java/com/zeromail/api/security/AdminRequestBody.java");

    @Test
    void master_key_controller_exposes_admin_only_contract() throws IOException {
        assertThat(Files.exists(CONTROLLER)).isTrue();

        String controller = Files.readString(CONTROLLER);
        assertThat(controller)
                .contains("@RequestMapping(\"/api/admin/master-keys\")")
                .contains("@PreAuthorize(\"hasRole('ADMIN')\")")
                .contains("AdminContext.currentOrThrow()")
                .contains("@GetMapping")
                .contains("@PostMapping(\"/{provider}/edit-session\")")
                .contains("@PostMapping(\"/{provider}/test-connection\")")
                .contains("@PutMapping(\"/{provider}\")")
                .contains("@PostMapping(\"/{provider}/rotate\")")
                .contains("@PutMapping(\"/feature-default\")");
    }

    @Test
    void plaintext_carrying_request_dtos_are_marked_as_admin_request_bodies() throws IOException {
        assertThat(Files.exists(ADMIN_REQUEST_BODY)).isTrue();
        for (Path requestPath : new Path[] {SET_REQUEST, ROTATE_REQUEST, TEST_REQUEST}) {
            assertThat(Files.exists(requestPath)).isTrue();
            String requestSource = Files.readString(requestPath);
            assertThat(requestSource)
                    .contains("@AdminRequestBody")
                    .contains("plaintextKey")
                    .doesNotContain("maskedKey");
        }
    }
}
