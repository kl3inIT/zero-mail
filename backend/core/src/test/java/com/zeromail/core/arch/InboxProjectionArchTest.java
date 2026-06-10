package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architectural boundary tests for the inbox projection module (Phase A wave 4).
 *
 * <ol>
 *   <li>Only {@code InboxProjectionWriteService} may call the projection repository's native
 *       UPSERT. Direct JPA {@code save*} or raw {@code upsertProjection} calls from anywhere else
 *       would bypass the cipher and the AAD construction, breaking the single-entry contract.
 *   <li>Only the Pub/Sub delivery listener (currently {@code GmailDeliveryProcessingService}) and
 *       the {@code InboxBackfillService} may call {@code InboxProjectionWriteService.upsert}. Phase
 *       B will add the read-swap path; until then any other caller is a scope creep.
 *   <li>Neither {@code GmailInboxProjectionRepository} nor {@code GmailInboxSyncStateRepository}
 *       may be injected outside the {@code core.inbox} package or the listener package — the
 *       projection is an owned read model, not a generic data store.
 * </ol>
 *
 * <p>Privacy log lint ("no plaintext field name in a log call") is handled separately by {@link
 * InboxProjectionPrivacyLogTest} since ArchUnit's call inspection cannot easily distinguish a
 * literal SLF4J argument from any other String parameter.
 */
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class InboxProjectionArchTest {

    @ArchTest
    static final ArchRule only_inbox_projection_write_service_may_invoke_native_upsert_projection =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.zeromail.core.inbox.usecases")
                    .should()
                    .callMethod(
                            "com.zeromail.core.inbox.persistence.GmailInboxProjectionRepository",
                            "upsertProjection",
                            "java.util.UUID",
                            "java.lang.String",
                            "java.lang.String",
                            "byte[]",
                            "byte[]",
                            "byte[]",
                            "byte[]",
                            "byte[]",
                            "boolean",
                            "java.time.Instant",
                            "java.lang.String[]",
                            "java.lang.String",
                            "boolean",
                            "long",
                            "java.time.Instant",
                            "java.time.Instant")
                    .because(
                            "InboxProjectionWriteService is the single entry point; bypassing it would skip"
                                    + " the cipher + AAD + sender-hash + expires_at derivation.");

    @ArchTest
    static final ArchRule projection_repository_only_injected_inside_inbox_module =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "com.zeromail.core.inbox..", "com.zeromail.core.gmail.usecases")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(
                            "com.zeromail.core.inbox.persistence.GmailInboxProjectionRepository")
                    .because(
                            "Inbox projection repository is an owned read model — only the inbox module"
                                    + " and the Pub/Sub delivery hook in core.gmail.usecases may depend on it.");

    @ArchTest
    static final ArchRule sync_state_repository_only_injected_inside_inbox_module =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "com.zeromail.core.inbox..", "com.zeromail.core.gmail.usecases")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(
                            "com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository")
                    .because(
                            "Sync state cursor belongs to the inbox module; the lazy backfill trigger in"
                                    + " RecentInboxReadService is the only external reader allowed.");

    @ArchTest
    static final ArchRule only_listener_and_backfill_invoke_projection_write_service_upsert =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "com.zeromail.core.inbox.usecases", "com.zeromail.core.gmail.usecases")
                    .should()
                    .callMethod(
                            "com.zeromail.core.inbox.usecases.InboxProjectionWriteService",
                            "upsert",
                            "com.zeromail.core.inbox.usecases.InboxProjectionUpsertCommand")
                    .because(
                            "Allowed callers: GmailDeliveryProcessingService (Pub/Sub observed hook) and"
                                    + " InboxBackfillService. Phase B may add the read-swap path; any other caller"
                                    + " is scope creep.");

    /**
     * Phase B Wave 4 — the projection cipher's {@code encrypt} and {@code decrypt} entry points
     * carry tenant + message-scoped AAD; any caller outside the dedicated read/write services would
     * either reconstruct that AAD ad-hoc (drift risk) or skip it entirely (decrypt-anywhere risk).
     * Tests are excluded by {@code ImportOption.DoNotIncludeTests} so cipher round-trip fixtures
     * can still exercise the API directly.
     */
    @ArchTest
    static final ArchRule only_read_and_write_services_call_projection_cipher_encrypt =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.zeromail.core.inbox.usecases")
                    .should()
                    .callMethod(
                            "com.zeromail.core.inbox.usecases.InboxProjectionCipher",
                            "encrypt",
                            "java.lang.String",
                            "java.util.UUID",
                            "java.lang.String",
                            "com.zeromail.core.inbox.domain.EncryptedField")
                    .because(
                            "InboxProjectionWriteService is the only production encrypt caller; any"
                                    + " other call site would bypass the AAD construction.");

    @ArchTest
    static final ArchRule only_read_and_write_services_call_projection_cipher_decrypt =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.zeromail.core.inbox.usecases")
                    .should()
                    .callMethod(
                            "com.zeromail.core.inbox.usecases.InboxProjectionCipher",
                            "decrypt",
                            "byte[]",
                            "java.util.UUID",
                            "java.lang.String",
                            "com.zeromail.core.inbox.domain.EncryptedField")
                    .because(
                            "InboxProjectionReadService is the only production decrypt caller; any"
                                    + " other call site would either reconstruct the AAD ad-hoc or skip"
                                    + " it entirely, both of which break the cipher boundary.");

    /**
     * Phase B Wave 4 — mark-read is the only projection mutator added in Wave 2; restrict callers
     * to the single triage write helper so future code does not start mirroring Gmail label edits
     * into the projection without going through the same Gmail-first / DB-second ordering and
     * idempotency contract.
     */
    @ArchTest
    static final ArchRule only_triage_gmail_writer_invokes_projection_mark_read =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "com.zeromail.core.inbox.usecases", "com.zeromail.core.triage.usecases")
                    .should()
                    .callMethod(
                            "com.zeromail.core.inbox.usecases.InboxProjectionWriteService",
                            "markRead",
                            "java.util.UUID",
                            "java.lang.String")
                    .because(
                            "TriageGmailWriter is the single Gmail-write boundary that mirrors mark-read"
                                    + " into the projection. Any other caller would skip the Gmail label modify"
                                    + " step, leaving Gmail and the projection in divergent states.");

    /**
     * Phase B Wave 2 follow-up — the generic label add/remove projection mirrors carry the same
     * Gmail-first / DB-second ordering contract as mark-read. Restrict callers to the single triage
     * write boundary so no other code mutates the projection's {@code label_ids} without first
     * confirming the change in Gmail.
     */
    @ArchTest
    static final ArchRule only_triage_gmail_writer_invokes_projection_add_label =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "com.zeromail.core.inbox.usecases", "com.zeromail.core.triage.usecases")
                    .should()
                    .callMethod(
                            "com.zeromail.core.inbox.usecases.InboxProjectionWriteService",
                            "addLabel",
                            "java.util.UUID",
                            "java.lang.String",
                            "java.lang.String")
                    .because(
                            "TriageGmailWriter is the single Gmail-write boundary that mirrors label adds"
                                    + " into the projection after Gmail confirms; any other caller would let the"
                                    + " projection diverge from the Gmail label set.");

    @ArchTest
    static final ArchRule only_triage_gmail_writer_invokes_projection_remove_label =
            noClasses()
                    .that()
                    .resideOutsideOfPackages(
                            "com.zeromail.core.inbox.usecases", "com.zeromail.core.triage.usecases")
                    .should()
                    .callMethod(
                            "com.zeromail.core.inbox.usecases.InboxProjectionWriteService",
                            "removeLabel",
                            "java.util.UUID",
                            "java.lang.String",
                            "java.lang.String")
                    .because(
                            "TriageGmailWriter is the single Gmail-write boundary that mirrors label removes"
                                    + " into the projection after Gmail confirms; any other caller would let the"
                                    + " projection diverge from the Gmail label set.");
}
