package com.zeromail.core.llm.byok;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.zeromail.core.llm.gateway.springai.ProviderConnectionTester;
import org.junit.jupiter.api.Test;

class ProviderConnectionTesterSingleBindingTest {

    @Test
    void adminAndUserConnectionTestsUseProviderConnectionTester() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackages(
                                "..core.admin.mkey.usecases..", "..core.llm.byok..")
                        .and()
                        .areNotAssignableTo(ProviderConnectionTester.class)
                        .should()
                        .callMethodWhere(providerConnectionTesterProbeCall())
                        .because(
                                "Only admin master-key tests and user BYOK tests may probe provider keys")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static DescribedPredicate<JavaMethodCall> providerConnectionTesterProbeCall() {
        return new DescribedPredicate<>("ProviderConnectionTester.probeConnection") {
            @Override
            public boolean test(JavaMethodCall methodCall) {
                return methodCall.getTarget().getName().equals("probeConnection")
                        && methodCall
                                .getTarget()
                                .getOwner()
                                .isAssignableTo(ProviderConnectionTester.class);
            }
        };
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
