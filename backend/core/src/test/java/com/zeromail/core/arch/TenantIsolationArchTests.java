package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
public class TenantIsolationArchTests {

    @ArchTest
    static final ArchRule no_threadlocal = noClasses()
            .that().resideInAnyPackage("..api..", "..worker..", "..core..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ThreadLocal")
            .because("FND-01/02: use ScopedValue, not ThreadLocal");

    @ArchTest
    static final ArchRule fanout_via_helper = noClasses()
            .that().resideOutsideOfPackage("..core.tenant.concurrency..")
            .should().callMethod(Thread.class, "ofVirtual")
            .orShould().callMethod(CompletableFuture.class, "supplyAsync", Supplier.class)
            .orShould().callMethod(CompletableFuture.class, "runAsync", Runnable.class)
            .because("FND-01: fan-out must re-bind tenant via TenantAwareTaskScope");

    @ArchTest
    static final ArchRule no_native_sql = noClasses()
            .that().resideOutsideOfPackage("..core.persistence.lowlevel..")
            .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>(
                    "EntityManager.createNativeQuery(...)") {
                @Override
                public boolean test(JavaMethodCall call) {
                    return call.getTarget().getName().equals("createNativeQuery")
                            && call.getTarget().getOwner().isAssignableTo("jakarta.persistence.EntityManager");
                }
            })
            .because("discriminator tenancy is not auto-applied to native SQL");
}
