package com.zeromail.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class OnlyOneGmailSendCallSiteTest {

    private static final String ALLOWED_SEND_CALL_SITE =
            "com.zeromail.core.chat.confirm.send.AllowedSendCallSite";
    private static final String ASSISTANT_SEND_EXECUTOR_PACKAGE =
            "com.zeromail.core.chat.confirm.send";
    private static final String GMAIL_MESSAGES_OWNER = "Gmail.Users.Messages";
    private static final String GMAIL_DRAFTS_OWNER = "Gmail.Users.Drafts";

    @Test
    void gmail_send_call_site_count_is_armed_for_wave_four_flip() {
        JavaClasses importedClasses = importProductionClasses();

        long callSiteCount =
                importedClasses.stream()
                        .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                        .filter(OnlyOneGmailSendCallSiteTest::isGmailSendCall)
                        .count();

        // Count flips 0->1 in Wave 4 atomic group [ATOMIC-GROUP: arch01-flip];
        // do not edit this number in isolation.
        assertThat(callSiteCount).isEqualTo(0L);
    }

    @Test
    void future_gmail_send_call_site_must_live_in_assistant_executor_package() {
        JavaClasses importedClasses = importProductionClasses();

        importedClasses.stream()
                .flatMap(
                        javaClass ->
                                javaClass.getMethodCallsFromSelf().stream()
                                        .filter(OnlyOneGmailSendCallSiteTest::isGmailSendCall)
                                        .map(methodCall -> new SendCallSite(javaClass, methodCall)))
                .forEach(
                        sendCallSite -> {
                            String originOwnerName = sendCallSite.originOwner().getName();
                            assertThat(originOwnerName)
                                    .as(
                                            "Gmail send call origin must be the assistant send executor")
                                    .startsWith(ASSISTANT_SEND_EXECUTOR_PACKAGE + ".");
                            assertThat(
                                            sendCallSite
                                                    .originOwner()
                                                    .isAnnotatedWith(ALLOWED_SEND_CALL_SITE))
                                    .as(
                                            "Gmail send call origin must carry "
                                                    + ALLOWED_SEND_CALL_SITE)
                                    .isTrue();
                        });
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }

    private static boolean isGmailSendCall(JavaMethodCall methodCall) {
        String targetOwnerName = methodCall.getTargetOwner().getName().replace('$', '.');
        return methodCall.getName().equals("send")
                && (targetOwnerName.endsWith(GMAIL_MESSAGES_OWNER)
                        || targetOwnerName.endsWith(GMAIL_DRAFTS_OWNER));
    }

    private record SendCallSite(JavaClass originOwner, JavaMethodCall methodCall) {}
}
