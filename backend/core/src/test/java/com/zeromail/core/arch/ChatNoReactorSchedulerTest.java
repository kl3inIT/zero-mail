package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class ChatNoReactorSchedulerTest {

    private static final Set<String> FORBIDDEN_SCHEDULER_METHODS =
            Set.of("boundedElastic", "parallel", "single");

    @ArchTest
    static final ArchRule chat_must_not_use_raw_reactor_schedulers =
            noClasses()
                    .that()
                    .resideInAPackage("..core.chat..")
                    .should()
                    .callMethodWhere(
                            new DescribedPredicate<JavaMethodCall>(
                                    "raw reactor scheduler factory calls") {
                                @Override
                                public boolean test(JavaMethodCall methodCall) {
                                    return methodCall
                                                    .getTargetOwner()
                                                    .isAssignableTo(
                                                            "reactor.core.scheduler.Schedulers")
                                            && FORBIDDEN_SCHEDULER_METHODS.contains(
                                                    methodCall.getName());
                                }
                            })
                    .because(
                            "ARCH-05: chat streaming must preserve TenantContext through "
                                    + "TenantAwareReactorScheduler")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule chat_must_not_call_subscribe_on_directly =
            noClasses()
                    .that()
                    .resideInAPackage("..core.chat..")
                    .and()
                    .resideOutsideOfPackage("..core.chat.llm..")
                    .should()
                    .callMethodWhere(
                            new DescribedPredicate<JavaMethodCall>(
                                    "direct Reactor subscribeOn(...) calls") {
                                @Override
                                public boolean test(JavaMethodCall methodCall) {
                                    return methodCall.getName().equals("subscribeOn");
                                }
                            })
                    .because(
                            "ARCH-05: chat streaming must use the tenant-aware scheduler adapter, "
                                    + "not direct Reactor scheduler fan-out")
                    .allowEmptyShould(true);
}
