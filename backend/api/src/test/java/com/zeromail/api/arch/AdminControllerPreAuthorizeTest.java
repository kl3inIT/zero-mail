package com.zeromail.api.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

class AdminControllerPreAuthorizeTest {

    @Test
    void every_admin_rest_controller_has_class_level_admin_pre_authorize() {
        JavaClasses productionClasses = importProductionClasses();
        productionClasses.stream()
                .filter(
                        javaClass ->
                                javaClass
                                        .getPackageName()
                                        .startsWith("com.zeromail.api.controllers.admin"))
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                .forEach(AdminControllerPreAuthorizeTest::assertAdminPreAuthorize);
    }

    private static void assertAdminPreAuthorize(JavaClass javaClass) {
        Optional<PreAuthorize> preAuthorize =
                javaClass.tryGetAnnotationOfType(PreAuthorize.class).toOptional();

        assertThat(preAuthorize)
                .as(javaClass.getName() + " must declare class-level @PreAuthorize")
                .isPresent();
        assertThat(preAuthorize.orElseThrow().value())
                .as(javaClass.getName() + " must require ROLE_ADMIN")
                .contains("hasRole('ADMIN')");
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
