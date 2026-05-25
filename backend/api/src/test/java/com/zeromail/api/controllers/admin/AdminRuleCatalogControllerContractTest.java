package com.zeromail.api.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminRuleCatalogControllerContractTest {

    private static final Path CONTROLLER =
            Path.of(
                    "src/main/java/com/zeromail/api/controllers/admin/AdminRuleCatalogController.java");
    private static final Path PERSONA_RESPONSE =
            Path.of(
                    "src/main/java/com/zeromail/api/dto/admin/rulecatalog/RuleCatalogPersonaAdminResponse.java");
    private static final Path EXAMPLE_RESPONSE =
            Path.of(
                    "src/main/java/com/zeromail/api/dto/admin/rulecatalog/RuleCatalogExampleAdminResponse.java");
    private static final Path ACTION_RESPONSE =
            Path.of(
                    "src/main/java/com/zeromail/api/dto/admin/rulecatalog/RuleCatalogActionDescriptorAdminResponse.java");

    @Test
    void admin_rule_catalog_controller_is_admin_only_and_delegates_to_core_service()
            throws IOException {
        assertThat(Files.exists(CONTROLLER)).isTrue();

        String controller = Files.readString(CONTROLLER);
        assertThat(controller)
                .contains("@RequestMapping(\"/api/admin/rule-catalog\")")
                .contains("@PreAuthorize(\"hasRole('ADMIN')\")")
                .contains("AdminContext.currentOrThrow()")
                .contains("RuleCatalogAdminService")
                .contains("@GetMapping(\"/personas\")")
                .contains("@PostMapping(\"/personas\")")
                .contains("@PutMapping(\"/personas/{personaId}\")")
                .contains("@PatchMapping(\"/personas/{personaId}/enabled\")")
                .contains("@PutMapping(\"/personas/reorder\")")
                .contains("@PostMapping(\"/personas/{personaId}/examples\")")
                .contains("@PutMapping(\"/examples/{exampleId}\")")
                .contains("@PatchMapping(\"/examples/{exampleId}/enabled\")")
                .contains("@PutMapping(\"/actions/{actionKey}\")")
                .contains("@PatchMapping(\"/actions/{actionKey}/enabled\")")
                .contains("@PutMapping(\"/actions/reorder\")");
    }

    @Test
    void admin_rule_catalog_dtos_expose_bilingual_fields_without_mail_content_shape()
            throws IOException {
        assertThat(Files.exists(PERSONA_RESPONSE)).isTrue();
        assertThat(Files.exists(EXAMPLE_RESPONSE)).isTrue();
        assertThat(Files.exists(ACTION_RESPONSE)).isTrue();

        String personaResponse = Files.readString(PERSONA_RESPONSE);
        String exampleResponse = Files.readString(EXAMPLE_RESPONSE);
        String actionResponse = Files.readString(ACTION_RESPONSE);
        assertThat(personaResponse)
                .contains("displayNameEn")
                .contains("displayNameVi")
                .doesNotContain("body")
                .doesNotContain("rawContent");
        assertThat(exampleResponse)
                .contains("exampleTextEn")
                .contains("exampleTextVi")
                .doesNotContain("body")
                .doesNotContain("rawContent");
        assertThat(actionResponse)
                .contains("labelEn")
                .contains("labelVi")
                .contains("descriptionEn")
                .contains("descriptionVi")
                .doesNotContain("body")
                .doesNotContain("rawContent");
    }
}
