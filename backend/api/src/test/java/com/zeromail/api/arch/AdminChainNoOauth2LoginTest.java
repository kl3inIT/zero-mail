package com.zeromail.api.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AdminChainNoOauth2LoginTest {

    private static final Path SECURITY_CONFIG =
            Path.of("src/main/java/com/zeromail/api/security/SecurityConfig.java");

    @Test
    void admin_security_chain_does_not_enable_oauth2_login() throws IOException {
        String source = Files.readString(SECURITY_CONFIG);
        String adminChainBody = methodBody(source, "adminChain");

        assertThat(adminChainBody).doesNotContain(".oauth2Login");
        assertThat(adminChainBody).contains(".webAuthn");
    }

    @Test
    void user_security_chain_does_not_enable_webauthn() throws IOException {
        String source = Files.readString(SECURITY_CONFIG);
        String userChainBody = methodBody(source, "chain");

        assertThat(userChainBody).doesNotContain(".webAuthn");
        assertThat(userChainBody).contains(".oauth2Login");
    }

    private static String methodBody(String source, String methodName) {
        String methodBody = optionalMethodBody(source, methodName);
        assertThat(methodBody).as("method " + methodName + " exists").isNotBlank();
        return methodBody;
    }

    private static String optionalMethodBody(String source, String methodName) {
        Pattern methodStartPattern =
                Pattern.compile(
                        "\\b"
                                + Pattern.quote(methodName)
                                + "\\s*\\([^)]*\\)\\s*throws?[^\\{]*\\{|\\b"
                                + Pattern.quote(methodName)
                                + "\\s*\\([^)]*\\)\\s*\\{");
        Matcher matcher = methodStartPattern.matcher(source);
        if (!matcher.find()) {
            return "";
        }

        int bodyStart = matcher.end() - 1;
        int depth = 0;
        for (int characterIndex = bodyStart; characterIndex < source.length(); characterIndex++) {
            char sourceCharacter = source.charAt(characterIndex);
            if (sourceCharacter == '{') {
                depth++;
            } else if (sourceCharacter == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, characterIndex + 1);
                }
            }
        }
        throw new IllegalStateException("Could not parse method body for " + methodName);
    }
}
