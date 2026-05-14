package com.zeromail.worker.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ResendBoundaryArchTest {

    @Test
    void resend_sdk_imports_stay_inside_worker_email_notification_adapter() {
        JavaClasses importedClasses = importProductionClasses();

        noClasses()
                .that()
                .resideOutsideOfPackage("..worker.notification.email..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.resend..")
                .because("Resend is the email channel adapter, not a core domain dependency.")
                .check(importedClasses);
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
