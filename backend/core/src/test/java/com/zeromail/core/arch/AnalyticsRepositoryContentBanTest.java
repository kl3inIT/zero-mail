package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AnalyticsRepositoryContentBanTest {

    private static final List<String> FORBIDDEN_QUERY_TOKENS =
            List.of("body", "prompt", "completion", "embedding");

    @Test
    void analytics_module_does_not_depend_on_content_bearing_domains() {
        JavaClasses importedClasses = importProductionClasses();

        noClasses()
                .that()
                .resideInAPackage("..core.analytics..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..core.draft..", "..core.thread.persistence..", "..core.llm..")
                .because("analytics may aggregate metadata only, never message content surfaces")
                .check(importedClasses);
    }

    @Test
    void analytics_jdbc_queries_do_not_read_content_or_llm_columns() throws IOException {
        String serviceSource = Files.readString(resolveServiceSource()).toLowerCase(Locale.ROOT);

        for (String forbiddenQueryToken : FORBIDDEN_QUERY_TOKENS) {
            assertThat(serviceSource)
                    .as("AnalyticsSummaryQueryService must not mention %s", forbiddenQueryToken)
                    .doesNotContain(forbiddenQueryToken);
        }
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }

    private static Path resolveServiceSource() {
        Path moduleRelative =
                Path.of(
                        "src/main/java/com/zeromail/core/analytics/usecases/"
                                + "AnalyticsSummaryQueryService.java");
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of(
                "backend/core/src/main/java/com/zeromail/core/analytics/usecases/"
                        + "AnalyticsSummaryQueryService.java");
    }
}
