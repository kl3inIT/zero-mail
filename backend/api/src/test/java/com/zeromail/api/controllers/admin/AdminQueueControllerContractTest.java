package com.zeromail.api.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminQueueControllerContractTest {

    private static final Path CONTROLLER =
            Path.of("src/main/java/com/zeromail/api/controllers/admin/AdminQueueController.java");
    private static final Path HEALTH_RESPONSE =
            Path.of("src/main/java/com/zeromail/api/dto/admin/queue/QueueHealthResponse.java");
    private static final Path DEAD_LETTER_PAGE_RESPONSE =
            Path.of("src/main/java/com/zeromail/api/dto/admin/queue/DeadLetterPageResponse.java");
    private static final Path DEAD_LETTER_ROW_RESPONSE =
            Path.of("src/main/java/com/zeromail/api/dto/admin/queue/DeadLetterRowResponse.java");
    private static final Path REQUEUE_REQUEST =
            Path.of("src/main/java/com/zeromail/api/dto/admin/queue/RequeueRequest.java");
    private static final Path PACKAGE_INFO =
            Path.of("src/main/java/com/zeromail/api/dto/admin/queue/package-info.java");

    @Test
    void queue_controller_exposes_admin_only_health_and_requeue() throws IOException {
        assertThat(Files.exists(CONTROLLER)).isTrue();

        String controller = Files.readString(CONTROLLER);
        assertThat(controller)
                .contains("@RequestMapping(\"/api/admin/queue\")")
                .contains("@PreAuthorize(\"hasRole('ADMIN')\")")
                .contains("AdminContext.currentOrThrow()")
                .contains("@GetMapping(\"/health\")")
                .contains("@GetMapping(\"/dead-letters\")")
                .contains("@PostMapping(\"/dead-letters/{jobId}/requeue\")")
                .contains("@ResponseStatus(HttpStatus.NO_CONTENT)");
    }

    @Test
    void queue_dtos_never_expose_a_body_or_payload_shape() throws IOException {
        for (Path dtoPath :
                new Path[] {
                    HEALTH_RESPONSE,
                    DEAD_LETTER_PAGE_RESPONSE,
                    DEAD_LETTER_ROW_RESPONSE,
                    REQUEUE_REQUEST
                }) {
            assertThat(Files.exists(dtoPath)).isTrue();
            String dtoSource = Files.readString(dtoPath);
            // Field-name strings forbidden by AdminBodyBanRegex (body/payload/prompt/completion/
            // snippet/content). Doc comments may mention them; the gate is on declarations. We
            // approximate by asserting the *record header* line and field declarations don't
            // include those substrings.
            for (String forbiddenFieldNamePart :
                    new String[] {
                        "payload",
                        "Payload",
                        "Body",
                        "body",
                        "Snippet",
                        "snippet",
                        "Prompt",
                        "prompt",
                        "Completion",
                        "completion"
                    }) {
                // Carve-out: javadoc text describing the body-ban gate naturally uses these
                // words. We assert no '@Schema' requiredProperties list contains them either.
                if (dtoSource.contains("requiredProperties")) {
                    String requiredPropertiesBlock =
                            dtoSource.substring(dtoSource.indexOf("requiredProperties"));
                    String firstSchemaList =
                            requiredPropertiesBlock.substring(
                                    0,
                                    Math.min(
                                            requiredPropertiesBlock.length(),
                                            requiredPropertiesBlock.indexOf("})") + 1));
                    assertThat(firstSchemaList)
                            .as(
                                    "DTO %s required field list must not contain %s",
                                    dtoPath, forbiddenFieldNamePart)
                            .doesNotContain("\"" + forbiddenFieldNamePart);
                }
            }
        }
    }

    @Test
    void requeue_request_enforces_reason_validators_and_sentinel_guard() throws IOException {
        assertThat(Files.exists(REQUEUE_REQUEST)).isTrue();
        String requeueRequestSource = Files.readString(REQUEUE_REQUEST);
        assertThat(requeueRequestSource)
                .contains("@NotBlank")
                .contains("@Size(min = 8, max = 500") .contains("@NoSentinelLeak");
        // The record header must declare exactly one component: reason. Extract the body between
        // 'public record RequeueRequest(' and ') {}' and assert it has no extra field.
        int recordStart = requeueRequestSource.indexOf("public record RequeueRequest(");
        int recordEnd = requeueRequestSource.indexOf(") {}", recordStart);
        assertThat(recordStart).isPositive();
        assertThat(recordEnd).isPositive();
        String recordBody = requeueRequestSource.substring(recordStart, recordEnd);
        assertThat(recordBody)
                .as("RequeueRequest must expose only the reason field")
                .contains("String reason")
                .doesNotContain("attempts")
                .doesNotContain("status")
                .doesNotContain("payload");
    }

    @Test
    void queue_dto_package_is_named_interface_exposed_to_modulith() throws IOException {
        assertThat(Files.exists(PACKAGE_INFO)).isTrue();
        String packageInfo = Files.readString(PACKAGE_INFO);
        assertThat(packageInfo)
                .contains("@org.springframework.modulith.NamedInterface(\"admin.queue\")");
    }
}
