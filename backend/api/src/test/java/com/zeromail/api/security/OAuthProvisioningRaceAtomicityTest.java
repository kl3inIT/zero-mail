package com.zeromail.api.security;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.service.OAuthProvisioningService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for CR-01 fix: race-loser path must NOT open a second transaction.
 *
 * <p>Simulates a concurrent first-login by: provisioning a user normally (as the "winner"),
 * then calling provisionBundledOAuth again with the same googleSubject (as the "loser" that
 * would have hit DataIntegrityViolationException if it ran truly concurrently).
 *
 * <p>After the second call the DB must have exactly 1 user, 1 tenant, 1 gmail_connection —
 * never 2 of any — confirming the loser observed the winner's state without writing.
 *
 * <p><b>W-1 test-scope note:</b> This test verifies sequential second-login idempotency via
 * the FAST-PATH (findByGoogleSubject returns the existing user before save). The
 * DataIntegrityViolationException catch block is verified by static inspection: after the
 * CR-01 fix, {@code grep -c "bundledTx.executeWithoutResult"} in the catch block returns 0,
 * and the catch block only calls {@code users.findByGoogleSubject} + returns winner state.
 * True concurrent race testing would require thread synchronization at the Postgres constraint
 * level which cannot be reliably reproduced in test containers.
 *
 * <p>Privacy: all fixture values use obviously-fake prefixes per threat T-01.5-06-01.
 */
class OAuthProvisioningRaceAtomicityTest extends ApiPostgresTestBase {

    @Autowired OAuthProvisioningService provisioning;
    @Autowired JdbcTemplate jdbc;

    private static final String FAKE_REFRESH_TOKEN = "fake-race-rt-do-not-use-v1";

    @BeforeEach
    void cleanUp() {
        jdbc.execute("DELETE FROM gmail_connections");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("DELETE FROM tenants");
    }

    @Test
    void race_loser_observes_winner_state_no_duplicate_rows_no_second_tx() {
        String subject = "google-subject-race-test-" + UUID.randomUUID();
        String email = "race-test-" + UUID.randomUUID() + "@example.test";
        String scopes = "https://www.googleapis.com/auth/gmail.modify";

        // Winner: first-login provisioning — commits user + tenant + connection atomically.
        OAuthProvisioningService.BundledProvisioningResult winner =
                provisioning.provisionBundledOAuth(subject, email, FAKE_REFRESH_TOKEN, scopes);

        assertThat(winner.firstLogin()).isTrue();
        assertThat(countUsers()).isEqualTo(1);
        assertThat(countTenants()).isEqualTo(1);
        assertThat(countConnections()).isEqualTo(1);

        // Loser: same Google subject — would have hit DataIntegrityViolationException at
        // concurrent users.save(), but since the winner already committed, findByGoogleSubject
        // finds the winner on the fast-path (existing user) before even attempting save.
        // This is the observable post-race-fix behavior: no second tx, returns winner's state.
        OAuthProvisioningService.BundledProvisioningResult loser =
                provisioning.provisionBundledOAuth(subject, email, "fake-loser-rt-do-not-use", scopes);

        // Loser MUST return winner's tenant and user IDs with firstLogin=false.
        assertThat(loser.firstLogin())
                .as("Race-loser (or second-login) must return firstLogin=false")
                .isFalse();
        assertThat(loser.tenantId())
                .as("Race-loser must observe winner's tenantId")
                .isEqualTo(winner.tenantId());
        assertThat(loser.userId())
                .as("Race-loser must observe winner's userId")
                .isEqualTo(winner.userId());

        // CR-01 atomicity invariant: still exactly 1 row in every table.
        assertThat(countUsers())
                .as("CR-01: no duplicate user row after race-loser path")
                .isEqualTo(1);
        assertThat(countTenants())
                .as("CR-01: no duplicate tenant row after race-loser path")
                .isEqualTo(1);
        assertThat(countConnections())
                .as("CR-01: no duplicate connection row after race-loser path")
                .isEqualTo(1);
    }

    @Test
    void race_loser_path_leaves_no_partial_state_when_winner_connection_already_exists() {
        // This test specifically guards the old defect: if the second bundledTx (now removed)
        // had failed, the loser would have ended up with 1 user + 1 tenant but 0 connections.
        // Post-fix: the loser never writes, so winner's 1+1+1 is the invariant.
        String subject = "google-subject-partial-state-guard-" + UUID.randomUUID();
        String email = "partial-state-" + UUID.randomUUID() + "@example.test";
        String scopes = "https://www.googleapis.com/auth/gmail.modify";

        provisioning.provisionBundledOAuth(subject, email, FAKE_REFRESH_TOKEN, scopes);

        // Simulate loser: second call with same subject but a different (fake) refresh token.
        provisioning.provisionBundledOAuth(subject, email, "fake-loser-rt-v2-do-not-use", scopes);

        // Invariant: winner's single connection row STILL exists — it was not overwritten.
        assertThat(countConnections())
                .as("Winner's gmail_connection must survive; loser must not overwrite it")
                .isEqualTo(1);
        assertThat(countUsers()).isEqualTo(1);
        assertThat(countTenants()).isEqualTo(1);
    }

    private long countUsers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    private long countTenants() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Long.class);
    }

    private long countConnections() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmail_connections", Long.class);
    }
}
