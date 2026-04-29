---
phase: 02A-mail-ingestion
plan: "02"
type: execute
wave: 2
depends_on:
  - "02A-01"
files_modified:
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/InvalidGrantException.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java
  - backend/core/src/main/java/com/zeromail/core/account/service/OAuthProvisioningService.java
  - backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java
  - backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java
  - backend/worker/src/main/resources/application.yml
autonomous: true
requirements:
  - MAIL-01
  - MAIL-02
  - MAIL-05

must_haves:
  truths:
    - "GmailWatchScheduler runs every minute and registers/renews users.watch for CONNECTED rows with NULL or near-expiry watch_expires_at"
    - "After 3 consecutive watch failures, ingestion_health flips to WATCH_UNHEALTHY"
    - "Rows with WATCH_UNHEALTHY are still retried by GmailWatchScheduler; a later successful watch flips ingestion_health back to HEALTHY"
    - "recordWatchSuccess initializes last_synced_history_id from the returned watch_history_id only when the cursor is NULL; renewals preserve the existing cursor so queued/unprocessed Gmail history is not skipped"
    - "GmailHistoryProcessor polls pubsub_delivery every 1s and fans out to mail_message_observed"
    - "claimPendingBatch atomically updates rows to PROCESSING before returning them; worker processing does not rely on locks that were released when the repository method returned"
    - "History-404 advances last_synced_history_id to webhook_history_id and sets ingestion_health=HISTORY_LOST"
    - "ScopedValue.where(TENANT, tenantId).run(...) wraps every per-row operation in both schedulers"
    - "Token refresh uses direct POST to https://oauth2.googleapis.com/token (no OAuth2AuthorizedClientService)"
    - "GmailConnectionService has markHistoryLost, markWatchUnhealthy, clearForReconnect, recordWatchSuccess methods"
    - "GmailConnectionService has a DB-only markDisconnected method; invalid-grant paths call it instead of best-effort users.stop cleanup"
    - "User-initiated disconnect commits DISCONNECTED/watch-field cleanup before any best-effort Gmail users.stop call can run or fail"
    - "OAuthProvisioningService calls clearForReconnect after successful reconnect/upsert so HISTORY_LOST and WATCH_UNHEALTHY recover through the normal watch scheduler path"
    - "GmailDeliveryProcessingService.processDelivery is a PUBLIC @Transactional method — Spring AOP can intercept it; @Transactional on private methods is ineffective"
  artifacts:
    - path: "backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java"
      provides: "@Scheduled(cron=every-minute) watch register + renew unified"
      contains: "0 * * * * *"
    - path: "backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java"
      provides: "@Scheduled(fixedDelay=1000) history fan-out — tick() only; delegates processDelivery to GmailDeliveryProcessingService"
      contains: "fixedDelay"
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java"
      provides: "Gmail API client builder + token refresh"
      exports: ["buildGmailClient", "refreshAccessToken"]
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java"
      provides: "@Service @Transactional class with public processDelivery() method"
      contains: "@Transactional"
  key_links:
    - from: "GmailHistoryProcessor.tick()"
      to: "GmailDeliveryProcessingService.processDelivery()"
      via: "injected service call per row inside ScopedValue.run()"
      pattern: "deliveryProcessingService\\.processDelivery"
    - from: "GmailWatchScheduler"
      to: "GmailConnectionService.recordWatchSuccess"
      via: "success path after users.watch()"
      pattern: "recordWatchSuccess"
    - from: "GmailDeliveryProcessingService"
      to: "GmailConnectionService.markHistoryLost"
      via: "404 catch block"
      pattern: "markHistoryLost"
---

<objective>
Implement the two backend worker schedulers (GmailWatchScheduler + GmailHistoryProcessor), extend GmailConnectionService with new state-management methods, create GmailApiClientFactory for headless token refresh, and introduce GmailDeliveryProcessingService to own the per-delivery transaction boundary.

Purpose: These schedulers are the core of Phase 2A — they drive Gmail watch lifecycle and history fan-out.

Output: GmailWatchScheduler, GmailHistoryProcessor (thin tick-only), GmailDeliveryProcessingService (transaction owner), GmailApiClientFactory, GmailConnectionService extensions, and reconnect cleanup wiring in OAuthProvisioningService. `TenantService.setTriagePaused` is owned by Plan 03 so Plan 03 no longer depends on this worker plan.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/02A-mail-ingestion/02A-CONTEXT.md
@.planning/phases/02A-mail-ingestion/02A-RESEARCH.md
@.planning/phases/02A-mail-ingestion/02A-PATTERNS.md

<interfaces>
<!-- Existing service/scheduler patterns to follow -->
From backend/worker/src/main/java/com/zeromail/worker/HealthcheckScheduler.java:
```java
@Component
public class HealthcheckScheduler {
    private static final Logger log = LoggerFactory.getLogger(HealthcheckScheduler.class);
    @Scheduled(fixedRate = 60_000L)
    public void tick() {
        log.info("worker healthcheck tick");
    }
}
```

From backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java (existing methods):
```java
@Transactional
public void disconnect(UUID tenantId) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setStatus(GmailConnectionStatus.DISCONNECTED);
        c.setDisconnectedAt(Instant.now());
        connections.save(c);
    });
}

public GmailConnectionProjection currentStatus(UUID tenantId) { ... }
public void upsert(UUID tenantId, String googleEmail, ...) { ... }
```

GmailApiClientFactory needs:
- `Gmail buildGmailClient(String accessToken)` — uses `GoogleCredential` or `HttpCredentialsAdapter`
- `TokenRefreshResult refreshAccessToken(String decryptedRefreshToken)` — direct POST to https://oauth2.googleapis.com/token

Per RESEARCH.md Pattern 6 (token refresh):
```java
public record TokenRefreshResult(String accessToken, Instant expiresAt) {}
// POST to https://oauth2.googleapis.com/token
// 400 + body contains "invalid_grant" → throw InvalidGrantException
// 200 → parse access_token, expires_in
```

GmailConnectionRepository already has: findByTenantId(UUID)
Plan 01 adds the repository methods used here: atomic `claimPendingBatch(...)`, `insertObservedIfAbsent(...)`, and the native watch-renewal query.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: GmailApiClientFactory + GmailConnectionService extensions + reconnect cleanup + GmailDeliveryProcessingService</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java,
    backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java,
    backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java,
    backend/core/src/main/java/com/zeromail/core/account/service/OAuthProvisioningService.java
  </files>

  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java (full file — read BEFORE editing)
    - backend/core/src/main/java/com/zeromail/core/account/service/OAuthProvisioningService.java (full file — read BEFORE editing; reconnect path lives here)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java (new fields from Plan 01)
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 6 token refresh, Pattern 5 Gmail API shapes)
    - .planning/phases/02A-mail-ingestion/02A-PATTERNS.md (GmailConnectionService new methods, reconnect cleanup)
    - CLAUDE.md (Conventions: service-owned @Transactional, privacy logging format, Lombok-free)
  </read_first>

  <action>
**`GmailApiClientFactory.java`** — NEW file, package `com.zeromail.core.gmail.service`. `@Component`.

Constructor injects:
- `@Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId`
- `@Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret`

Methods:

```java
public Gmail buildGmailClient(String accessToken) throws IOException {
    try {
        GoogleCredentials credentials = GoogleCredentials.create(
            new AccessToken(accessToken, null));
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
        return new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(), requestInitializer)
            .setApplicationName("ZeroMail")
            .build();
    } catch (GeneralSecurityException e) {
        throw new IOException("Unable to initialize Gmail HTTP transport", e);
    }
}

public record TokenRefreshResult(String accessToken, Instant expiresAt) {}

public TokenRefreshResult refreshAccessToken(String decryptedRefreshToken) throws IOException {
    // Direct POST per RESEARCH.md Pattern 6 — no OAuth2AuthorizedClientService (no Authentication in scheduler)
    HttpClient httpClient = HttpClient.newHttpClient();
    String body = "grant_type=refresh_token"
        + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
        + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
        + "&refresh_token=" + URLEncoder.encode(decryptedRefreshToken, StandardCharsets.UTF_8);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://oauth2.googleapis.com/token"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    HttpResponse<String> response;
    try {
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Token refresh interrupted", e);
    }
    if (response.statusCode() == 200) {
        // Parse JSON: {"access_token":"...", "expires_in": 3600, "token_type":"Bearer"}
        // Use Jackson ObjectMapper (already available via Spring Boot)
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(response.body());
        String accessToken2 = node.get("access_token").asText();
        int expiresIn = node.path("expires_in").asInt(3600);
        return new TokenRefreshResult(accessToken2, Instant.now().plusSeconds(expiresIn - 60));
    }
    if (response.statusCode() == 400 && response.body().contains("invalid_grant")) {
        throw new InvalidGrantException("OAuth token revoked");
    }
    throw new IOException("Token refresh failed with status: " + response.statusCode());
}
```

`InvalidGrantException` is a custom checked or unchecked exception: `public class InvalidGrantException extends RuntimeException { public InvalidGrantException(String msg) { super(msg); } }` — declare in the same package or `com.zeromail.core.gmail.service`.

Privacy: NEVER log `decryptedRefreshToken`. NEVER log `accessToken`. Log only `event=gmail_token_refresh_failed tenantId={}` on error (tenantId comes from caller context).

Dependency note: Plan 01 owns Gradle wiring for this class. `backend/core/build.gradle.kts` must already expose `api(libs.google.api.services.gmail)` and `implementation(libs.google.auth.library.oauth2.http)`, and `gradle/libs.versions.toml` must already define those aliases. Do not proceed with this task if those aliases/dependencies are missing.

Testability note: Worker tests may inject a mocked `GmailApiClientFactory` instead of using a real generated Gmail client. If a test chooses to exercise the real factory, it must use a hermetic HTTP transport/root URL seam and must not call `https://gmail.googleapis.com` or `https://oauth2.googleapis.com` during tests.

**`GmailConnectionService.java`** — READ full current file first. ADD these methods following the existing `@Transactional` + `findByTenantId` + `save` pattern:

```java
@Transactional
public void markHistoryLost(UUID tenantId, Long newPointer) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setLastSyncedHistoryId(newPointer);
        c.setIngestionHealth(GmailIngestionHealth.HISTORY_LOST);
        connections.save(c);
    });
}

@Transactional
public void markWatchUnhealthy(UUID tenantId) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setIngestionHealth(GmailIngestionHealth.WATCH_UNHEALTHY);
        connections.save(c);
    });
}

@Transactional
public void recordWatchSuccess(UUID tenantId, Long watchHistoryId, Instant watchExpiresAt) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setWatchHistoryId(watchHistoryId);
        // Only initial registration/reconnect creates a fresh baseline. Regular renewals must not
        // advance the history-processing cursor past queued or unprocessed Pub/Sub deliveries.
        if (c.getLastSyncedHistoryId() == null) {
            c.setLastSyncedHistoryId(watchHistoryId);
        }
        c.setWatchExpiresAt(watchExpiresAt);
        c.setWatchRenewedAt(Instant.now());
        c.setWatchConsecutiveFailures(0);
        c.setIngestionHealth(GmailIngestionHealth.HEALTHY);
        connections.save(c);
    });
}

@Transactional
public void incrementWatchFailure(UUID tenantId) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setWatchConsecutiveFailures(c.getWatchConsecutiveFailures() + 1);
        connections.save(c);
    });
}

@Transactional
public void clearForReconnect(UUID tenantId) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setWatchExpiresAt(null);
        c.setWatchHistoryId(null);
        c.setLastSyncedHistoryId(null);
        c.setWatchConsecutiveFailures(0);
        c.setIngestionHealth(GmailIngestionHealth.HEALTHY);
        connections.save(c);
    });
}
```

Also split durable disconnect state from best-effort Gmail watch cleanup. This addresses the Cycle 2 review concern: invalid-grant paths must not call `users.stop()` or attempt another token refresh before the durable DB disconnect is recorded.

Implement these exact semantics:
- Add `public void markDisconnected(UUID tenantId)` as a DB-only method. It updates the `gmail_connections` row to `status=DISCONNECTED`, sets `disconnected_at=Instant.now()`, clears `watch_expires_at`, `watch_history_id`, and `watch_renewed_at`, resets `watch_consecutive_failures=0`, sets `ingestion_health=HEALTHY`, and saves the row. It MUST NOT call Gmail APIs, `GmailApiClientFactory`, `RefreshTokenCipher`, or `users.stop()`.
- Use a `TransactionTemplate` or equivalent explicit transaction boundary for `markDisconnected(UUID tenantId)` so the DB update is committed independently before optional cleanup. Do not rely on self-invoked `@Transactional` methods for this split.
- Keep `public void disconnect(UUID tenantId)` as the user/API disconnect orchestration method. It calls `markDisconnected(tenantId)` first, then calls private `tryStopWatch(UUID tenantId)` best-effort after the durable DB update has committed.
- Extract the stop call to `private void tryStopWatch(UUID tenantId)`. It may decrypt the stored refresh token, refresh/build the Gmail client, and call `gmail.users().stop("me").execute()`, but it wraps the entire path in try/catch, logs `event=gmail_watch_stop_failed tenantId={}` on failure, and never re-throws.
- Inject `GmailApiClientFactory`, `RefreshTokenCipher`, and `PlatformTransactionManager` (for `TransactionTemplate`) into this service as needed.
- Update every invalid-grant handler in this plan (`GmailDeliveryProcessingService` and `GmailWatchScheduler`) to call `connectionService.markDisconnected(tenantId)` instead of `connectionService.disconnect(tenantId)`. The invalid-grant path already proved the refresh token is unusable, so attempting `users.stop()` there is both redundant and risky.
- Existing user-facing disconnect flows such as `DisconnectController` continue to call `connectionService.disconnect(tenantId)` so explicit user disconnect still attempts watch cleanup best-effort.

Also add a native @Query to GmailConnectionRepository for the SKIP LOCKED watch scheduler query (or add as a new interface method). Add to `GmailConnectionRepository.java`:
```java
@Query(value = """
    SELECT * FROM gmail_connections
    WHERE status = 'CONNECTED'
    AND (watch_expires_at IS NULL OR watch_expires_at < NOW() + INTERVAL '24 hours')
    ORDER BY watch_renewed_at NULLS FIRST
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
@Transactional
List<GmailConnectionEntity> findConnectionsNeedingWatchRenewal(@Param("limit") int limit);
```
Do not filter out `watch_consecutive_failures >= 3`. The third failure sets `ingestion_health=WATCH_UNHEALTHY`, but renewal ticks continue retrying so the system can self-recover without requiring a manual reconnect for transient Gmail/API failures.

Also add monotonic-conditional UPDATE query to `GmailConnectionRepository`:
```java
@Modifying
@Query("UPDATE GmailConnectionEntity c SET c.lastSyncedHistoryId = :newId " +
       "WHERE c.tenantId = :tenantId AND " +
       "(c.lastSyncedHistoryId IS NULL OR c.lastSyncedHistoryId < :newId)")
@Transactional
int updateLastSyncedHistoryIdMonotonic(@Param("tenantId") UUID tenantId, @Param("newId") Long newId);
```

**`OAuthProvisioningService.java`** — READ the full file. In the existing reconnect path, after `connections.upsert(tenantId, email, grantedGmailScopes, envelope)` succeeds, call `connections.clearForReconnect(tenantId)` in the same tenant-bound transaction. This clears `last_synced_history_id`, `watch_expires_at`, `watch_history_id`, resets `watch_consecutive_failures`, and sets `ingestion_health=HEALTHY` so `GmailWatchScheduler` re-registers the watch on the next minute tick. Do not call Gmail APIs from the OAuth success path.

**`GmailDeliveryProcessingService.java`** — NEW `@Service @Transactional` class in `com.zeromail.core.gmail.service`. This class owns the per-delivery transaction boundary — extracted from GmailHistoryProcessor to avoid the `private @Transactional` Spring AOP interception bug (Spring AOP cannot intercept private methods; transaction never starts on a private method).

```java
package com.zeromail.core.gmail.service;

import ...;

@Service
@Transactional
public class GmailDeliveryProcessingService {

    private static final Logger log = LoggerFactory.getLogger(GmailDeliveryProcessingService.class);
    private static final long HISTORY_GAP_CAP = 500L;   // D-B6 bounded window

    private final PubSubDeliveryRepository deliveryRepository;
    private final MailMessageObservedRepository observedRepository;
    private final GmailConnectionService connectionService;
    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;

    public GmailDeliveryProcessingService(
            PubSubDeliveryRepository deliveryRepository,
            MailMessageObservedRepository observedRepository,
            GmailConnectionService connectionService,
            GmailConnectionRepository connectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            RefreshTokenCipher refreshTokenCipher) {
        this.deliveryRepository = deliveryRepository;
        this.observedRepository = observedRepository;
        this.connectionService = connectionService;
        this.connectionRepository = connectionRepository;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
    }

    // PUBLIC method — Spring AOP can intercept it; @Transactional takes effect.
    // DO NOT make this private. The transaction boundary wraps the entire delivery processing
    // including mail_message_observed inserts + last_synced_history_id update.
    public void processDelivery(PubSubDeliveryEntity delivery) {
        UUID tenantId = delivery.getTenantId();
        long webhookHistoryId = delivery.getHistoryId();

        try {
            GmailConnectionEntity conn = connectionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("No connection for tenantId: " + tenantId));

            String decryptedToken = refreshTokenCipher.decrypt(conn.getRefreshTokenEncrypted());
            GmailApiClientFactory.TokenRefreshResult tokenResult = gmailApiClientFactory.refreshAccessToken(decryptedToken);
            Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken());

            // D-B6 bounded history window
            long startHistoryId = conn.getLastSyncedHistoryId() != null
                ? conn.getLastSyncedHistoryId() : webhookHistoryId;
            if (webhookHistoryId - startHistoryId > HISTORY_GAP_CAP) {
                startHistoryId = webhookHistoryId - HISTORY_GAP_CAP;
                log.warn("event=gmail_history_gap_truncated tenantId={} skipped={}",
                    tenantId, (webhookHistoryId - startHistoryId));
            }

            ListHistoryResponse historyResponse = gmail.users()
                .history().list("me")
                .setStartHistoryId(BigInteger.valueOf(startHistoryId))
                .setHistoryTypes(List.of("messageAdded"))
                .setLabelId("INBOX")
                .setMaxResults(500L)
                .execute();

            if (historyResponse.getNextPageToken() != null) {
                log.warn("event=gmail_history_pagination_dropped tenantId={}", tenantId);
            }

            int newObservations = 0;
            List<History> historyList = historyResponse.getHistory();
            if (historyList != null) {
                for (History h : historyList) {
                    if (h.getMessagesAdded() == null) continue;
                    for (HistoryMessageAdded added : h.getMessagesAdded()) {
                        Message historyMsg = added.getMessage();
                        if (historyMsg == null || historyMsg.getId() == null) continue;

                        // history.list entries may contain only id/threadId. Fetch metadata only,
                        // with a fields mask that excludes snippet, payload, headers, body, and raw.
                        Message msg = gmail.users().messages()
                            .get("me", historyMsg.getId())
                            .setFormat("metadata")
                            .setFields("id,threadId,labelIds,internalDate")
                            .execute();
                        List<String> labelIds = msg.getLabelIds();
                        if (labelIds == null || !labelIds.contains("INBOX")) continue;

                        // D-B3: privacy floor — IDs + labels only, no content.
                        // Native INSERT ... ON CONFLICT DO NOTHING avoids rollback-only
                        // transactions from JPA duplicate-key exceptions.
                        int inserted = observedRepository.insertObservedIfAbsent(
                            tenantId,
                            msg.getId(),
                            msg.getThreadId(),
                            h.getId().longValue(),
                            labelIds.toArray(new String[0]),
                            msg.getInternalDate()  // nullable per schema
                        );
                        if (inserted == 1) {
                            newObservations++;
                        }
                    }
                }
            }

            // D-B5: monotonic-conditional history pointer advance
            connectionRepository.updateLastSyncedHistoryIdMonotonic(tenantId, webhookHistoryId);

            deliveryRepository.updateStatus(delivery.getId(), "PROCESSED");
            log.info("event=gmail_history_processed tenantId={} batch_size=1 new_observations={}",
                tenantId, newObservations);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                // D-D2: history-404 recovery — advance pointer, set HISTORY_LOST, mark PROCESSED
                connectionService.markHistoryLost(tenantId, webhookHistoryId);
                deliveryRepository.updateStatus(delivery.getId(), "PROCESSED");
                log.warn("event=gmail_history_lost tenantId={} expired_history_id={} new_pointer={}",
                    tenantId, delivery.getHistoryId(), webhookHistoryId);
            } else {
                handleRetryableFailure(delivery, tenantId, e);
            }
        } catch (InvalidGrantException e) {
            connectionService.markDisconnected(tenantId);
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn("event=gmail_oauth_revoked tenantId={}", tenantId);
        } catch (Exception e) {
            handleRetryableFailure(delivery, tenantId, e);
        }
    }

    private void handleRetryableFailure(PubSubDeliveryEntity delivery, UUID tenantId, Exception e) {
        int attempts = delivery.getAttempts(); // incremented atomically by claimPendingBatch
        if (attempts >= 3) {
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn("event=gmail_delivery_dead tenantId={} attempts={}", tenantId, attempts);
        } else {
            deliveryRepository.releaseForRetry(delivery.getId(), Instant.now().plusSeconds(30));
            log.warn("event=gmail_delivery_retry tenantId={} attempt={}", tenantId, attempts);
        }
    }
}
```

Privacy: NEVER log `conn.getRefreshTokenEncrypted()`, `decryptedToken`, token values, email addresses, `msg.getSnippet()`, subject, or any email content. Only log `tenantId` UUID + numeric counts.
  </action>

  <verify>
    <automated>./gradlew :backend:core:compileJava :backend:api:compileJava 2>&1 | grep -E "error:|BUILD|FAILED" | head -10</automated>
  </verify>

  <acceptance_criteria>
    - `GmailApiClientFactory.java` exists in `com.zeromail.core.gmail.service` package
    - `GmailApiClientFactory.java` contains `refreshAccessToken` method and `invalid_grant` check
    - `GmailApiClientFactory.buildGmailClient` handles `GoogleNetHttpTransport.newTrustedTransport()` checked exceptions by wrapping `GeneralSecurityException` in `IOException` or otherwise declaring a compile-safe checked-exception path
    - `GmailApiClientFactory.java` does NOT contain any log statement with `refreshToken`, `accessToken`, or `decryptedRefresh` variable contents
    - `GmailConnectionService.java` contains `markHistoryLost(`, `markWatchUnhealthy(`, `recordWatchSuccess(`, `clearForReconnect(`, `incrementWatchFailure(`, `markDisconnected(`
    - `GmailConnectionService.recordWatchSuccess` sets `lastSyncedHistoryId` from `watchHistoryId` only when the existing cursor is null; it does NOT advance a non-null cursor during renewal
    - `GmailConnectionService.markDisconnected` contains no `users().stop`, `refreshAccessToken`, `buildGmailClient`, or `RefreshTokenCipher` call path
    - `GmailConnectionService.disconnect` invokes durable `markDisconnected(tenantId)` before `tryStopWatch(tenantId)`
    - `tryStopWatch` catches all failures and logs `event=gmail_watch_stop_failed tenantId={}` without rethrowing
    - `GmailConnectionRepository.java` contains `findConnectionsNeedingWatchRenewal(` and `updateLastSyncedHistoryIdMonotonic(`
    - `findConnectionsNeedingWatchRenewal` does NOT contain `watch_consecutive_failures < 3`
    - `OAuthProvisioningService.java` contains `connections.clearForReconnect(tenantId)` in the reconnect/upsert path
    - `GmailDeliveryProcessingService.java` exists in `com.zeromail.core.gmail.service`, is annotated `@Service @Transactional`, and has a `public void processDelivery(PubSubDeliveryEntity delivery)` method — NOT private
    - `GmailDeliveryProcessingService.java` does NOT have `private void processDelivery` (the old Spring AOP bug pattern)
    - `GmailDeliveryProcessingService.java` invalid-grant catch calls `connectionService.markDisconnected(tenantId)`, not `connectionService.disconnect(tenantId)`
    - `GmailDeliveryProcessingService.java` uses `observedRepository.insertObservedIfAbsent` and does NOT catch `DataIntegrityViolationException` for message-level idempotency
    - `./gradlew :backend:core:compileJava` exits 0
  </acceptance_criteria>

  <done>GmailApiClientFactory, 6 new GmailConnectionService methods including DB-only markDisconnected, OAuthProvisioningService reconnect cleanup, and GmailDeliveryProcessingService (public @Transactional) all compile cleanly</done>
</task>

<task type="auto">
  <name>Task 2: GmailWatchScheduler + GmailHistoryProcessor (thin tick, delegates to GmailDeliveryProcessingService) + worker application.yml env vars</name>
  <files>
    backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java,
    backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java,
    backend/worker/src/main/resources/application.yml
  </files>

  <read_first>
    - backend/worker/src/main/java/com/zeromail/worker/HealthcheckScheduler.java (full file — scheduler pattern)
    - backend/worker/src/main/resources/application.yml (full file — existing env vars, add new ones)
    - backend/worker/src/main/java/com/zeromail/worker/WorkerApplication.java (scan packages confirmed)
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 4 SKIP LOCKED + ScopedValue, Pattern 5 Gmail API shapes)
    - .planning/phases/02A-mail-ingestion/02A-PATTERNS.md (GmailWatchScheduler adaptation, GmailHistoryProcessor adaptation)
    - .planning/phases/02A-mail-ingestion/02A-CONTEXT.md (D-C1, D-C2, D-C3, D-D2, D-B6)
    - CLAUDE.md (Conventions: privacy logging, Lombok-free, virtual threads)
  </read_first>

  <action>
**`GmailWatchScheduler.java`** — new `@Component` in `com.zeromail.worker`. `@Scheduled(cron = "0 * * * * *")` — every minute. Implements D-C1/D-C2 (async register+renew via unified query).

```java
@Component
public class GmailWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(GmailWatchScheduler.class);
    private static final int BATCH_SIZE = 50;
    private static final int FAILURE_THRESHOLD = 3;

    private final GmailConnectionRepository connectionRepository;
    private final GmailConnectionService connectionService;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    @Value("${google.pubsub.topic-name}")
    private String topicName;

    // Constructor injection (Lombok-free)

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        List<GmailConnectionEntity> batch = connectionRepository.findConnectionsNeedingWatchRenewal(BATCH_SIZE);
        for (GmailConnectionEntity conn : batch) {
            // ScopedValue binding per row — D-C1 / RESEARCH.md P-02
            ScopedValue.where(TenantContext.TENANT, conn.getTenantId().toString())
                       .run(() -> processWatchRenewal(conn));
        }
    }

    private void processWatchRenewal(GmailConnectionEntity conn) {
        UUID tenantId = conn.getTenantId();
        try {
            String decryptedToken = refreshTokenCipher.decrypt(conn.getRefreshTokenEncrypted());
            GmailApiClientFactory.TokenRefreshResult tokenResult = gmailApiClientFactory.refreshAccessToken(decryptedToken);
            Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken());

            WatchRequest watchRequest = new WatchRequest()
                .setLabelIds(List.of("INBOX"))               // D-C3: INBOX only
                .setLabelFilterBehavior("include")
                .setTopicName(topicName);

            WatchResponse response = gmail.users().watch("me", watchRequest).execute();
            long watchHistoryId = response.getHistoryId().longValue();
            Instant watchExpiresAt = Instant.ofEpochMilli(response.getExpiration());

            connectionService.recordWatchSuccess(tenantId, watchHistoryId, watchExpiresAt);
            log.info("event=gmail_watch_renewed tenantId={}", tenantId);

        } catch (InvalidGrantException e) {
            // Token revoked — DB-only disconnect. Do not call users.stop() on invalid-grant paths.
            connectionService.markDisconnected(tenantId);
            log.warn("event=gmail_watch_invalid_grant tenantId={}", tenantId);
        } catch (Exception e) {
            // Retryable failure path (D-C4: 3 strikes → WATCH_UNHEALTHY)
            connectionService.incrementWatchFailure(tenantId);
            int failures = conn.getWatchConsecutiveFailures() + 1;
            if (failures >= FAILURE_THRESHOLD) {
                connectionService.markWatchUnhealthy(tenantId);
                log.warn("event=gmail_watch_unhealthy_threshold tenantId={}", tenantId);
            } else {
                log.warn("event=gmail_watch_renewal_failed tenantId={} attempt={}", tenantId, failures);
            }
        }
    }
}
```

Privacy: NEVER log `conn.getRefreshTokenEncrypted()`, `decryptedToken`, `tokenResult.accessToken()`, `conn.getGoogleEmail()`. Only log `tenantId` UUID.

**`GmailHistoryProcessor.java`** — new `@Component` in `com.zeromail.worker`. `@Scheduled(fixedDelay = 1_000L)` — 1s after previous tick completes.

The `tick()` method is a thin scan loop only — it does NOT own any `@Transactional` logic. Per-delivery transaction is owned by `GmailDeliveryProcessingService.processDelivery()` (a PUBLIC method on a separate Spring bean — allows Spring AOP to intercept the `@Transactional` annotation correctly). This avoids the `private @Transactional` bug where Spring AOP cannot intercept private methods and the transaction never starts.

```java
@Component
public class GmailHistoryProcessor {

    private static final Logger log = LoggerFactory.getLogger(GmailHistoryProcessor.class);
    private static final int BATCH_SIZE = 50;
    private static final int LOCK_SECONDS = 120;

    private final PubSubDeliveryRepository deliveryRepository;
    private final GmailDeliveryProcessingService deliveryProcessingService;

    // Constructor injection (Lombok-free)
    public GmailHistoryProcessor(PubSubDeliveryRepository deliveryRepository,
                                  GmailDeliveryProcessingService deliveryProcessingService) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryProcessingService = deliveryProcessingService;
    }

    @Scheduled(fixedDelay = 1_000L)
    public void tick() {
        List<PubSubDeliveryEntity> batch = deliveryRepository.claimPendingBatch(BATCH_SIZE, LOCK_SECONDS);
        for (PubSubDeliveryEntity delivery : batch) {
            // ScopedValue binds TenantContext per row before delegate call
            ScopedValue.where(TenantContext.TENANT, delivery.getTenantId().toString())
                       .run(() -> deliveryProcessingService.processDelivery(delivery));
        }
    }
}
```

Privacy: Only `deliveryProcessingService.processDelivery` handles the Gmail API and DB writes; privacy logging lives there. This class does NOT log any tenant-specific content beyond what GmailDeliveryProcessingService emits.

**`backend/worker/src/main/resources/application.yml`** — READ the current file. ADD env vars with `:?` fail-fast (per Phase 01.5 P08 pattern):
```yaml
google:
  pubsub:
    topic-name: ${GOOGLE_PUBSUB_TOPIC_NAME:?GOOGLE_PUBSUB_TOPIC_NAME env var is required}
```
Also ensure `spring.threads.virtual.enabled: true` is present (or add if missing — check first).
  </action>

  <verify>
    <automated>./gradlew :backend:worker:compileJava :backend:worker:test --tests "*GmailWatchSchedulerTest*" --tests "*GmailHistoryProcessorTest*" 2>&1 | grep -E "BUILD|PASSED|FAILED|error:" | head -20</automated>
  </verify>

  <acceptance_criteria>
    - `GmailWatchScheduler.java` exists in `com.zeromail.worker` and contains `"0 * * * * *"` cron expression
    - `GmailWatchScheduler.java` contains `ScopedValue.where` wrapping `processWatchRenewal`
    - `GmailWatchScheduler.java` contains `setLabelIds(List.of("INBOX"))` — D-C3 INBOX-only
    - `GmailWatchScheduler.java` does NOT contain any log statement referencing `refreshToken`, `accessToken`, or `googleEmail`
    - `GmailHistoryProcessor.java` contains `fixedDelay` and `ScopedValue.where`
    - `GmailHistoryProcessor.java` calls `claimPendingBatch(BATCH_SIZE, LOCK_SECONDS)` and claimed rows are already `PROCESSING`
    - `GmailHistoryProcessor.java` injects `GmailDeliveryProcessingService` and calls `deliveryProcessingService.processDelivery(delivery)` — NOT an inline private method
    - `GmailHistoryProcessor.java` does NOT contain `@Transactional` annotation (transaction lives in GmailDeliveryProcessingService)
    - `GmailDeliveryProcessingService.java` calls `history().list("me").setLabelId("INBOX")` and then `messages().get("me", messageId).setFormat("metadata").setFields("id,threadId,labelIds,internalDate")` before checking `labelIds`
    - `GmailWatchScheduler.java` invalid-grant catch calls `connectionService.markDisconnected(tenantId)`, not `connectionService.disconnect(tenantId)`
    - `GmailDeliveryProcessingService.java` contains `event=gmail_history_gap_truncated` and `event=gmail_history_lost` on 404 catch
    - `GmailDeliveryProcessingService.java` does NOT contain subject, from, body, snippet, or email address in any log statement
    - `backend/worker/src/main/resources/application.yml` contains `GOOGLE_PUBSUB_TOPIC_NAME:?`
    - `./gradlew :backend:worker:compileJava` exits 0
    - `GmailWatchSchedulerTest` contains `renew_existingHistoryPointer_doesNotAdvanceLastSyncedHistoryId()` or equivalent coverage
    - GmailWatchSchedulerTest and GmailHistoryProcessorTest pass GREEN (using MockGmailHistoryServer)
  </acceptance_criteria>

  <done>Both schedulers compile; GmailHistoryProcessor delegates to GmailDeliveryProcessingService (public @Transactional); GmailWatchSchedulerTest and GmailHistoryProcessorTest pass GREEN; worker application.yml has env-var fail-fast config</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Worker → Google OAuth endpoint | Token refresh crosses network boundary to accounts.google.com |
| Worker → Gmail API | History.list + watch calls cross network boundary |
| Scheduled thread → TenantContext | ScopedValue must be explicitly bound per row |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-04 | Information Disclosure | GmailApiClientFactory.refreshAccessToken | mitigate | `decryptedRefreshToken` parameter never logged; only `event=gmail_token_refresh_failed tenantId={}` on error |
| T-05 | Information Disclosure | GmailDeliveryProcessingService per-message loop | mitigate | Only `gmail_message_id`, `gmail_thread_id`, `history_id`, `label_ids`, `internal_date` stored — no subject/from/body/snippet in code or logs |
| T-09 | Denial of Service | GmailWatchScheduler retry loop | mitigate | After 3 failures sets WATCH_UNHEALTHY but renewal query continues retrying; a later success resets failures + HEALTHY so transient outages self-recover |
| T-11 | Tampering | GmailDeliveryProcessingService crash recovery | mitigate | Atomic PROCESSING claim reclaims expired PROCESSING rows via `locked_until < NOW()` + native `ON CONFLICT DO NOTHING` + monotonic pointer update = exactly-once observation on restart; PUBLIC @Transactional ensures atomicity |
| T-03 | Elevation of Privilege | history gap truncation | accept | D-B6 explicitly accepts dropped-gap messages — 500-item cap prevents runaway; logged for admin visibility |
</threat_model>

<verification>
After this plan:
- `./gradlew :backend:worker:compileJava` exits 0
- `./gradlew :backend:worker:test --tests "*GmailWatchSchedulerTest*"` exits 0 (GREEN)
- `./gradlew :backend:worker:test --tests "*GmailHistoryProcessorTest*"` exits 0 (GREEN)
- `GmailWatchScheduler.java` passes grep: `grep -v '^//' GmailWatchScheduler.java | grep -v '^#' | grep -c 'googleEmail\|refreshToken\|accessToken'` = 0 on log lines
- `GmailHistoryProcessor.java` does NOT contain `@Transactional` annotation (transaction is in GmailDeliveryProcessingService)
</verification>

<success_criteria>
GmailWatchScheduler and GmailHistoryProcessor compile and their Wave 0 tests turn GREEN. GmailHistoryProcessor delegates per-delivery work to GmailDeliveryProcessingService (public @Transactional — Spring AOP intercepts correctly). GmailConnectionService has 6 new state-management methods, including DB-only markDisconnected, and recordWatchSuccess initializes last_synced_history_id from watch_history_id only when the cursor is null. Regular watch renewal preserves a non-null last_synced_history_id so queued/unprocessed Gmail history cannot be skipped. Invalid-grant paths never call best-effort users.stop cleanup. OAuth reconnect clears watch state for retry. Worker application.yml has GOOGLE_PUBSUB_TOPIC_NAME fail-fast var.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-02-SUMMARY.md`
</output>
