---
phase: 02A-mail-ingestion
plan: "02"
type: execute
wave: 2
depends_on:
  - "02A-01"
files_modified:
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java
  - backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java
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
    - "GmailHistoryProcessor polls pubsub_delivery every 1s and fans out to mail_message_observed"
    - "History-404 advances last_synced_history_id to webhook_history_id and sets ingestion_health=HISTORY_LOST"
    - "ScopedValue.where(TENANT, tenantId).run(...) wraps every per-row operation in both schedulers"
    - "Token refresh uses direct POST to https://oauth2.googleapis.com/token (no OAuth2AuthorizedClientService)"
    - "GmailConnectionService has markHistoryLost, markWatchUnhealthy, clearForReconnect, recordWatchSuccess methods"
  artifacts:
    - path: "backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java"
      provides: "@Scheduled(cron=every-minute) watch register + renew unified"
      contains: "0 * * * * *"
    - path: "backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java"
      provides: "@Scheduled(fixedDelay=1000) history fan-out"
      contains: "fixedDelay"
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java"
      provides: "Gmail API client builder + token refresh"
      exports: ["buildGmailClient", "refreshAccessToken"]
  key_links:
    - from: "GmailHistoryProcessor"
      to: "TenantContext.TENANT"
      via: "ScopedValue.where(...).run(...) per row"
      pattern: "ScopedValue\\.where.*TENANT"
    - from: "GmailWatchScheduler"
      to: "GmailConnectionService.recordWatchSuccess"
      via: "success path after users.watch()"
      pattern: "recordWatchSuccess"
    - from: "GmailHistoryProcessor"
      to: "GmailConnectionService.markHistoryLost"
      via: "404 catch block"
      pattern: "markHistoryLost"
---

<objective>
Implement the two backend worker schedulers (GmailWatchScheduler + GmailHistoryProcessor), extend GmailConnectionService with new state-management methods, and create GmailApiClientFactory for headless token refresh.

Purpose: These schedulers are the core of Phase 2A — they drive Gmail watch lifecycle and history fan-out.

Output: GmailWatchScheduler, GmailHistoryProcessor, GmailApiClientFactory, GmailConnectionService extensions, TenantService.setTriagePaused.
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
Add: findByStatusAndWatchExpiresAtIsNullOrWatchExpiresAtBefore(GmailConnectionStatus, Instant) — or use @Query native SQL.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: GmailApiClientFactory + GmailConnectionService extensions + TenantService.setTriagePaused</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java,
    backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java,
    backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java
  </files>

  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java (full file — read BEFORE editing)
    - backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java (full file — read BEFORE editing)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java (new fields from Plan 01)
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 6 token refresh, Pattern 5 Gmail API shapes)
    - .planning/phases/02A-mail-ingestion/02A-PATTERNS.md (GmailConnectionService new methods, TenantService.setTriagePaused)
    - CLAUDE.md (Conventions: service-owned @Transactional, privacy logging format, Lombok-free)
  </read_first>

  <action>
**`GmailApiClientFactory.java`** — NEW file, package `com.zeromail.core.gmail.service`. `@Component`.

Constructor injects:
- `@Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId`
- `@Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret`

Methods:

```java
public Gmail buildGmailClient(String accessToken) {
    GoogleCredentials credentials = GoogleCredentials.create(
        new AccessToken(accessToken, null));
    HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
    return new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(), requestInitializer)
        .setApplicationName("ZeroMail")
        .build();
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
        c.setWatchConsecutiveFailures(0);
        c.setIngestionHealth(GmailIngestionHealth.HEALTHY);
        connections.save(c);
    });
}
```

Also extend the existing `disconnect(UUID tenantId)` method to call `users.stop()` BEST-EFFORT OUTSIDE the main transaction:
- Extract the stop() call to a private `tryStopWatch(UUID tenantId)` method
- The tryStopWatch method calls the Gmail client, wraps in try-catch, logs `event=gmail_watch_stop_failed tenantId={}` on failure — never re-throws
- Call `tryStopWatch(tenantId)` BEFORE the `connections.findByTenantId(tenantId).ifPresent(...)` block, so DB commit happens even if stop() fails
- Inject `GmailApiClientFactory` and `RefreshTokenCipher` into this service for the stop() call

Also add a native @Query to GmailConnectionRepository for the SKIP LOCKED watch scheduler query (or add as a new interface method). Add to `GmailConnectionRepository.java`:
```java
@Query(value = """
    SELECT * FROM gmail_connections
    WHERE status = 'CONNECTED'
    AND (watch_expires_at IS NULL OR watch_expires_at < NOW() + INTERVAL '24 hours')
    AND watch_consecutive_failures < 3
    ORDER BY watch_renewed_at NULLS FIRST
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
@Transactional
List<GmailConnectionEntity> findConnectionsNeedingWatchRenewal(@Param("limit") int limit);
```

Also add monotonic-conditional UPDATE query to `GmailConnectionRepository`:
```java
@Modifying
@Query("UPDATE GmailConnectionEntity c SET c.lastSyncedHistoryId = :newId " +
       "WHERE c.tenantId = :tenantId AND " +
       "(c.lastSyncedHistoryId IS NULL OR c.lastSyncedHistoryId < :newId)")
@Transactional
int updateLastSyncedHistoryIdMonotonic(@Param("tenantId") UUID tenantId, @Param("newId") Long newId);
```

**`TenantService.java`** — READ full current file. ADD:
```java
@Transactional
public void setTriagePaused(UUID tenantId, boolean paused) {
    tenants.findById(tenantId).ifPresent(t -> {
        t.setTriagePaused(paused);
        tenants.save(t);
    });
}
```
Privacy log goes in the controller (not here).
  </action>

  <verify>
    <automated>./gradlew :backend:core:compileJava :backend:api:compileJava 2>&1 | grep -E "error:|BUILD|FAILED" | head -10</automated>
  </verify>

  <acceptance_criteria>
    - `GmailApiClientFactory.java` exists in `com.zeromail.core.gmail.service` package
    - `GmailApiClientFactory.java` contains `refreshAccessToken` method and `invalid_grant` check
    - `GmailApiClientFactory.java` does NOT contain any log statement with `refreshToken`, `accessToken`, or `decryptedRefresh` variable contents
    - `GmailConnectionService.java` contains `markHistoryLost(`, `markWatchUnhealthy(`, `recordWatchSuccess(`, `clearForReconnect(`, `incrementWatchFailure(`
    - `GmailConnectionRepository.java` contains `findConnectionsNeedingWatchRenewal(` and `updateLastSyncedHistoryIdMonotonic(`
    - `TenantService.java` contains `setTriagePaused(UUID tenantId, boolean paused)`
    - `./gradlew :backend:core:compileJava` exits 0
  </acceptance_criteria>

  <done>GmailApiClientFactory, 5 new GmailConnectionService methods, TenantService.setTriagePaused all compile cleanly</done>
</task>

<task type="auto">
  <name>Task 2: GmailWatchScheduler + GmailHistoryProcessor + worker application.yml env vars</name>
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
            // Token revoked — disconnect
            connectionService.disconnect(tenantId);
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

```java
@Component
public class GmailHistoryProcessor {

    private static final Logger log = LoggerFactory.getLogger(GmailHistoryProcessor.class);
    private static final int BATCH_SIZE = 50;
    private static final long HISTORY_GAP_CAP = 500L;   // D-B6 bounded window

    private final PubSubDeliveryRepository deliveryRepository;
    private final MailMessageObservedRepository observedRepository;
    private final GmailConnectionService connectionService;
    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;

    // Constructor injection (Lombok-free)

    @Scheduled(fixedDelay = 1_000L)
    public void tick() {
        List<PubSubDeliveryEntity> batch = deliveryRepository.claimPendingBatch(BATCH_SIZE);
        for (PubSubDeliveryEntity delivery : batch) {
            ScopedValue.where(TenantContext.TENANT, delivery.getTenantId().toString())
                       .run(() -> processDelivery(delivery));
        }
    }

    @Transactional
    private void processDelivery(PubSubDeliveryEntity delivery) {
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
                        Message msg = added.getMessage();
                        List<String> labelIds = msg.getLabelIds();
                        if (labelIds == null || !labelIds.contains("INBOX")) continue;

                        // D-B3: privacy floor — IDs + labels only, no content
                        MailMessageObservedEntity observed = new MailMessageObservedEntity(
                            tenantId,
                            msg.getId(),
                            msg.getThreadId(),
                            h.getId().longValue(),
                            labelIds.toArray(new String[0]),
                            msg.getInternalDate()  // nullable per schema
                        );
                        // ON CONFLICT DO NOTHING semantics via saveAndFlush + ignore duplicate exception
                        try {
                            observedRepository.save(observed);
                            newObservations++;
                        } catch (DataIntegrityViolationException ignored) {
                            // Duplicate — idempotent (D-A5)
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
            connectionService.disconnect(tenantId);
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn("event=gmail_oauth_revoked tenantId={}", tenantId);
        } catch (Exception e) {
            handleRetryableFailure(delivery, tenantId, e);
        }
    }

    private void handleRetryableFailure(PubSubDeliveryEntity delivery, UUID tenantId, Exception e) {
        int attempts = delivery.getAttempts() + 1;
        if (attempts >= 3) {
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn("event=gmail_delivery_dead tenantId={} attempts={}", tenantId, attempts);
        } else {
            deliveryRepository.incrementAttempts(delivery.getId());
            log.warn("event=gmail_delivery_retry tenantId={} attempt={}", tenantId, attempts);
        }
    }
}
```

Add `incrementAttempts` to `PubSubDeliveryRepository`:
```java
@Modifying
@Query("UPDATE PubSubDeliveryEntity d SET d.attempts = d.attempts + 1 WHERE d.id = :id")
@Transactional
int incrementAttempts(@Param("id") UUID id);
```

Privacy: NEVER log `conn.getRefreshTokenEncrypted()`, `decryptedToken`, token values, email addresses, `msg.getSnippet()`, subject, or any email content. Only log `tenantId` UUID + numeric counts.

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
    - `GmailHistoryProcessor.java` contains `HISTORY_GAP_CAP` = 500 and `event=gmail_history_gap_truncated`
    - `GmailHistoryProcessor.java` contains `event=gmail_history_lost` on 404 catch
    - `GmailHistoryProcessor.java` does NOT contain subject, from, body, snippet, or email address in any log statement
    - `backend/worker/src/main/resources/application.yml` contains `GOOGLE_PUBSUB_TOPIC_NAME:?`
    - `./gradlew :backend:worker:compileJava` exits 0
    - GmailWatchSchedulerTest and GmailHistoryProcessorTest pass GREEN (using MockGmailHistoryServer)
  </acceptance_criteria>

  <done>Both schedulers compile; GmailWatchSchedulerTest and GmailHistoryProcessorTest pass GREEN; worker application.yml has 3-env-var fail-fast config</done>
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
| T-05 | Information Disclosure | GmailHistoryProcessor per-message loop | mitigate | Only `gmail_message_id`, `gmail_thread_id`, `history_id`, `label_ids`, `internal_date` stored — no subject/from/body/snippet in code or logs |
| T-09 | Denial of Service | GmailWatchScheduler retry loop | mitigate | `watch_consecutive_failures < 3` WHERE clause gates retries; after 3 failures sets WATCH_UNHEALTHY + stops standard renewal until clearForReconnect called |
| T-11 | Tampering | GmailHistoryProcessor crash recovery | mitigate | `ON CONFLICT DO NOTHING` + monotonic pointer update = exactly-once observation on restart |
| T-03 | Elevation of Privilege | history gap truncation | accept | D-B6 explicitly accepts dropped-gap messages — 500-item cap prevents runaway; logged for admin visibility |
</threat_model>

<verification>
After this plan:
- `./gradlew :backend:worker:compileJava` exits 0
- `./gradlew :backend:worker:test --tests "*GmailWatchSchedulerTest*"` exits 0 (GREEN)
- `./gradlew :backend:worker:test --tests "*GmailHistoryProcessorTest*"` exits 0 (GREEN)
- `GmailWatchScheduler.java` passes grep: `grep -v '^//' | grep -c 'googleEmail\|refreshToken\|accessToken' = 0` on log lines
</verification>

<success_criteria>
GmailWatchScheduler and GmailHistoryProcessor compile and their Wave 0 tests turn GREEN. GmailConnectionService has 5 new state-management methods. TenantService.setTriagePaused added. Worker application.yml has GOOGLE_PUBSUB_TOPIC_NAME fail-fast var.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-02-SUMMARY.md`
</output>
