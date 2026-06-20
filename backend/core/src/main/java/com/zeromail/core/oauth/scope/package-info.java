/**
 * OAuth scope ledger — the canonical list of every Google OAuth scope this product is allowed to
 * request. Lives as a code-first Java enum ({@link com.zeromail.core.oauth.scope.GoogleOAuthScope})
 * so adding/removing a scope is a typed, reviewable code change.
 *
 * <p>The companion {@code OAuthScopeAllowListTest} (under {@code src/test/java}) performs a
 * source-text scan over {@code backend/{api,core,worker}/src/main/java} and fails CI if any
 * production source file outside this package contains a literal Google scope URL of the form
 * {@code https://www.googleapis.com/auth/...}. See {@link
 * com.zeromail.core.oauth.scope.GoogleOAuthScope} class JavaDoc for usage.
 *
 * <p>Per Phase 12 RESEARCH §Pitfall 1, ArchUnit's byte-code model does not capture constant string
 * arguments to method calls, so the scanner is implemented as a JUnit 5 file walk + regex rather
 * than {@code noClasses().that()...containAnyConstantMatching(...)}.
 */
@NamedInterface("scope")
package com.zeromail.core.oauth.scope;

import org.springframework.modulith.NamedInterface;
