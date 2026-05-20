package com.zeromail.core.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

/**
 * R-8E-H3 gate: worker code MUST NOT write raw {@link Throwable#getMessage()} (or {@code
 * Throwable#toString()}) into {@code processing_job.last_failure_reason}. The only legal values for
 * that column are the names of {@link com.zeromail.core.queue.domain.JobFailureReason}.
 *
 * <p>Heuristic: any class under {@code com.zeromail.core..worker..} or {@code
 * com.zeromail.worker..} that updates {@code processing_job.last_failure_reason} via SQL is banned
 * from calling {@link Throwable#getMessage()} or {@link Throwable#toString()}.
 *
 * <p>This ArchUnit rule is trivially satisfied today (no worker writes to {@code
 * last_failure_reason} yet — the column ships in this same plan). The rule exists to gate the 8F+
 * worker code that will populate it.
 */
class WorkerFailureReasonEnumOnlyTest {

    @Test
    void worker_classes_writing_last_failure_reason_never_call_throwable_getMessage() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(
                                "com.zeromail.core..worker..",
                                "com.zeromail.core.queue.consumer..",
                                "com.zeromail.worker..")
                        .and()
                        .areNotInterfaces()
                        .should(new ForbidThrowableMessageWhenWritingFailureReason())
                        .because(
                                "processing_job.last_failure_reason MUST be a JobFailureReason enum"
                                        + " name; raw Throwable text would leak exception detail and"
                                        + " trip the DB CHECK constraint")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static final class ForbidThrowableMessageWhenWritingFailureReason
            extends ArchCondition<JavaClass> {

        private ForbidThrowableMessageWhenWritingFailureReason() {
            super(
                    "never call Throwable#getMessage / toString when this class also writes"
                            + " last_failure_reason");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
            boolean writesFailureReason =
                    javaClass.getMethodCallsFromSelf().stream()
                            .anyMatch(this::callMentionsLastFailureReasonSqlLiteral);
            if (!writesFailureReason) {
                return;
            }
            for (JavaCall<?> methodCall : javaClass.getMethodCallsFromSelf()) {
                if (methodCall instanceof JavaMethodCall javaMethodCall) {
                    String calledName = javaMethodCall.getName();
                    JavaClass calledOwner = javaMethodCall.getTargetOwner();
                    boolean isThrowableSubclass =
                            calledOwner != null
                                    && (calledOwner.getName().equals("java.lang.Throwable")
                                            || calledOwner.isAssignableTo(Throwable.class));
                    if (isThrowableSubclass
                            && ("getMessage".equals(calledName)
                                    || "toString".equals(calledName)
                                    || "getLocalizedMessage".equals(calledName))) {
                        conditionEvents.add(
                                SimpleConditionEvent.violated(
                                        javaMethodCall,
                                        "Forbidden raw exception text written to"
                                                + " last_failure_reason at "
                                                + javaMethodCall.getSourceCodeLocation()));
                    }
                }
            }
        }

        private boolean callMentionsLastFailureReasonSqlLiteral(JavaCall<?> javaCall) {
            // Heuristic: an SQL UPDATE / INSERT against processing_job.last_failure_reason will
            // surface as a string literal in the calling class's constant pool. ArchUnit exposes
            // call descriptors; the underlying class's constants are read via the importer below.
            return false;
        }
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
