package com.zeromail.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboundGmailSendBoundaryTest {

    private static final String ALLOWED_SEND_CALL_SITE =
            "com.zeromail.core.outbound.usecases.AllowedSendCallSite";
    private static final String OUTBOUND_GATEWAY_CLASS =
            "com.zeromail.core.outbound.usecases.GmailOutboundSendGateway";
    private static final String GMAIL_MESSAGES_OWNER = "Gmail.Users.Messages";
    private static final String GMAIL_DRAFTS_OWNER = "Gmail.Users.Drafts";
    private static final List<String> FORBIDDEN_SEND_PACKAGE_PREFIXES =
            List.of(
                    "com.zeromail.api.",
                    "com.zeromail.worker.",
                    "com.zeromail.core.admin.",
                    "com.zeromail.core.rules.",
                    "com.zeromail.core.triage.");

    @Test
    void gmail_messages_send_is_owned_only_by_shared_outbound_gateway() {
        List<SendCallSite> sendCallSites = sendCallSites();

        assertThat(sendCallSites)
                .singleElement()
                .satisfies(
                        sendCallSite -> {
                            assertThat(sendCallSite.originOwner().getName())
                                    .isEqualTo(OUTBOUND_GATEWAY_CLASS);
                            assertThat(
                                            sendCallSite
                                                    .originOwner()
                                                    .isAnnotatedWith(ALLOWED_SEND_CALL_SITE))
                                    .isTrue();
                        });
    }

    @Test
    void controllers_admin_rules_triage_and_worker_packages_cannot_call_gmail_send() {
        assertThat(sendCallSites())
                .noneMatch(
                        sendCallSite ->
                                FORBIDDEN_SEND_PACKAGE_PREFIXES.stream()
                                        .anyMatch(
                                                forbiddenPackagePrefix ->
                                                        sendCallSite
                                                                .originOwner()
                                                                .getName()
                                                                .startsWith(
                                                                        forbiddenPackagePrefix)));
    }

    private static List<SendCallSite> sendCallSites() {
        JavaClasses importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");
        return importedClasses.stream()
                .flatMap(
                        javaClass ->
                                javaClass.getMethodCallsFromSelf().stream()
                                        .filter(OutboundGmailSendBoundaryTest::isGmailSendCall)
                                        .map(methodCall -> new SendCallSite(javaClass, methodCall)))
                .toList();
    }

    private static boolean isGmailSendCall(JavaMethodCall methodCall) {
        String targetOwnerName = methodCall.getTargetOwner().getName().replace('$', '.');
        return methodCall.getName().equals("send")
                && (targetOwnerName.endsWith(GMAIL_MESSAGES_OWNER)
                        || targetOwnerName.endsWith(GMAIL_DRAFTS_OWNER));
    }

    private record SendCallSite(JavaClass originOwner, JavaMethodCall methodCall) {}
}
