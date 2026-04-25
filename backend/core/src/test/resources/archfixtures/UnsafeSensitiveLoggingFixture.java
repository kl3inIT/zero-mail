// Negative fixture for the sensitiveLogGuard. NOT compiled — lives under
// src/test/resources/archfixtures so the build only reads its bytes for the source-scan
// guard. Each line below is intentionally unsafe and must trigger a violation.
package archfixtures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UnsafeSensitiveLoggingFixture {

    private static final Logger log = LoggerFactory.getLogger(UnsafeSensitiveLoggingFixture.class);

    void unsafeBody(String body) {
        log.info("got body={}", body);
    }

    void unsafeRefreshToken(String refreshToken) {
        log.warn("refresh failed for refreshToken={}", refreshToken);
    }

    void unsafePrompt(String prompt) {
        log.debug("prompt={}", prompt);
    }

    void unsafeCompletion(String completion) {
        log.error("model returned completion={}", completion);
    }

    void unsafeAccessToken(String accessToken) {
        log.trace("accessToken={}", accessToken);
    }
}
