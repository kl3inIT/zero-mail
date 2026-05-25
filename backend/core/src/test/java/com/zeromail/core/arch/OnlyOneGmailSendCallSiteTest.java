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
            "com.zeromail.core.outbound.usecases.AllowedSendCallSite";
    private static final String OUTBOUND_GATEWAY_PACKAGE = "com.zeromail.core.outbound";
    private static final String GMAIL_MESSAGES_OWNER = "Gmail.Users.Messages";
    private static final String GMAIL_DRAFTS_OWNER = "Gmail.Users.Drafts";

    @Test
    void exactly_one_gmail_send_call_site_exists() {
        JavaClasses importedClasses = importProductionClasses();

        long callSiteCount =
                importedClasses.stream()
                        .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                        .filter(OnlyOneGmailSendCallSiteTest::isGmailSendCall)
                        .count();

        assertThat(callSiteCount).isEqualTo(1L);
    }

    @Test
    void gmail_send_call_site_must_live_in_outbound_gateway_package() {
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
                                            "Gmail send call origin must be the shared outbound gateway")
                                    .satisfies(
                                            originName ->
                                                    assertThat(
                                                                    originName.startsWith(
                                                                            OUTBOUND_GATEWAY_PACKAGE
                                                                                    + "."))
                                                            .isTrue());
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
        // Scope: this test enforces the chat module's single-send-call-site invariant. Other
        // modules
        // (Phase 8 cleanup `UnsubscribeMailtoSender`, future SEED-009 send paths) are gated by the
        // broader allow-list test `GmailWriteBoundaryTest`. Scoping to the chat package keeps both
        // tests deterministic without churning the chat-specific assertion shape.
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail.core.chat");
    }

    private static boolean isGmailSendCall(JavaMethodCall methodCall) {
        String targetOwnerName = methodCall.getTargetOwner().getName().replace('$', '.');
        return methodCall.getName().equals("send")
                && (targetOwnerName.endsWith(GMAIL_MESSAGES_OWNER)
                        || targetOwnerName.endsWith(GMAIL_DRAFTS_OWNER));
    }

    private record SendCallSite(JavaClass originOwner, JavaMethodCall methodCall) {}
}
