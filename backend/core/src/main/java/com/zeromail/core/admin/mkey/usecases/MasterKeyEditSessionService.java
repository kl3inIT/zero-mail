package com.zeromail.core.admin.mkey.usecases;

import com.zeromail.core.llm.domain.LlmProvider;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MasterKeyEditSessionService {

    private static final Duration EDIT_SESSION_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "zeromail:mkey:edit-session:";

    private final Supplier<StringRedisTemplate> stringRedisTemplateSupplier;
    private final Supplier<String> tokenSupplier;
    private final Clock clock;

    @Autowired
    public MasterKeyEditSessionService(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this(
                stringRedisTemplateProvider::getIfAvailable,
                new SecureTokenSupplier(),
                Clock.systemUTC());
    }

    public MasterKeyEditSessionService(
            Supplier<StringRedisTemplate> stringRedisTemplateSupplier,
            Supplier<String> tokenSupplier,
            Clock clock) {
        this.stringRedisTemplateSupplier =
                Objects.requireNonNull(
                        stringRedisTemplateSupplier,
                        "stringRedisTemplateSupplier must not be null");
        this.tokenSupplier =
                Objects.requireNonNull(tokenSupplier, "tokenSupplier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public EditSession mint(UUID actorId, LlmProvider provider) {
        String token = requireText(tokenSupplier.get(), "token");
        redisTemplate().opsForValue().set(key(actorId, provider), token, EDIT_SESSION_TTL);
        return new EditSession(token, clock.instant().plus(EDIT_SESSION_TTL));
    }

    public Optional<String> validate(UUID actorId, LlmProvider provider, String token) {
        String storedToken = redisTemplate().opsForValue().get(key(actorId, provider));
        if (storedToken == null || !storedToken.equals(token)) {
            return Optional.empty();
        }
        return Optional.of(storedToken);
    }

    public Optional<String> consume(UUID actorId, LlmProvider provider, String token) {
        String storedToken = redisTemplate().opsForValue().getAndDelete(key(actorId, provider));
        if (storedToken == null || !storedToken.equals(token)) {
            return Optional.empty();
        }
        return Optional.of(storedToken);
    }

    private StringRedisTemplate redisTemplate() {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateSupplier.get();
        if (stringRedisTemplate == null) {
            throw new EditSessionUnavailableException();
        }
        return stringRedisTemplate;
    }

    private static String key(UUID actorId, LlmProvider provider) {
        return KEY_PREFIX + actorId + ":" + provider.id();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public record EditSession(String token, Instant expiresAt) {}

    public static class EditSessionUnavailableException extends RuntimeException {}

    private static final class SecureTokenSupplier implements Supplier<String> {

        private final SecureRandom secureRandom = new SecureRandom();

        @Override
        public String get() {
            byte[] tokenBytes = new byte[32];
            secureRandom.nextBytes(tokenBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        }
    }
}
