package com.zeromail.api.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminSpendControllerContractTest {

    private static final Path CONTROLLER =
            Path.of("src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java");
    private static final Path DASHBOARD_RESPONSE =
            Path.of("src/main/java/com/zeromail/api/dto/admin/spend/SpendDashboardResponse.java");
    private static final Path KPI_RESPONSE =
            Path.of("src/main/java/com/zeromail/api/dto/admin/spend/SpendKpiResponse.java");
    private static final Path STACK_BAR_RESPONSE =
            Path.of(
                    "src/main/java/com/zeromail/api/dto/admin/spend/ProviderStackBarRowResponse.java");
    private static final Path DONUT_RESPONSE =
            Path.of(
                    "src/main/java/com/zeromail/api/dto/admin/spend/FeatureDonutSliceResponse.java");
    private static final Path TOP_TENANT_RESPONSE =
            Path.of("src/main/java/com/zeromail/api/dto/admin/spend/TopTenantRowResponse.java");
    private static final Path PACKAGE_INFO =
            Path.of("src/main/java/com/zeromail/api/dto/admin/spend/package-info.java");

    @Test
    void spend_controller_exposes_admin_only_dashboard_and_csv_export() throws IOException {
        assertThat(Files.exists(CONTROLLER)).isTrue();

        String controller = Files.readString(CONTROLLER);
        assertThat(controller)
                .contains("@RequestMapping(\"/api/admin/spend\")")
                .contains("@PreAuthorize(\"hasRole('ADMIN')\")")
                .contains("AdminContext.currentOrThrow()")
                .contains("@GetMapping(\"/dashboard\")")
                .contains("@GetMapping(value = \"/dashboard/csv\"")
                .contains("writeReadEvent")
                .contains("SPEND_DASHBOARD");
    }

    @Test
    void spend_dtos_required_property_lists_have_no_body_or_payload_shape() throws IOException {
        for (Path dtoPath :
                new Path[] {
                    DASHBOARD_RESPONSE,
                    KPI_RESPONSE,
                    STACK_BAR_RESPONSE,
                    DONUT_RESPONSE,
                    TOP_TENANT_RESPONSE
                }) {
            assertThat(Files.exists(dtoPath)).isTrue();
            String dtoSource = Files.readString(dtoPath);
            if (dtoSource.contains("requiredProperties")) {
                String requiredPropertiesBlock =
                        dtoSource.substring(dtoSource.indexOf("requiredProperties"));
                String firstSchemaList =
                        requiredPropertiesBlock.substring(
                                0,
                                Math.min(
                                        requiredPropertiesBlock.length(),
                                        requiredPropertiesBlock.indexOf("})") + 1));
                for (String forbiddenFieldNamePart :
                        new String[] {
                            "payload",
                            "Payload",
                            "promptText",
                            "completionText",
                            "requestBody",
                            "responseBody",
                            "bodyText",
                            "contentText"
                        }) {
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
    void spend_dto_package_is_named_interface_exposed_to_modulith() throws IOException {
        assertThat(Files.exists(PACKAGE_INFO)).isTrue();
        String packageInfo = Files.readString(PACKAGE_INFO);
        assertThat(packageInfo)
                .contains("@org.springframework.modulith.NamedInterface(\"admin.spend\")");
    }

    @Test
    void spend_dashboard_response_carries_unknown_percent_and_classification_since()
            throws IOException {
        assertThat(Files.exists(DASHBOARD_RESPONSE)).isTrue();
        String dashboardSource = Files.readString(DASHBOARD_RESPONSE);
        assertThat(dashboardSource)
                .contains("unknownPercentOfTotal")
                .contains("rowLevelClassificationSince")
                .contains("kAnonymityFooterNote");
    }
}
