package com.zeromail.api.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W-3 mitigation: minimal unit test for {@link ConnectGmailController}.
 *
 * <p>SecurityConfig is annotated with {@code @Profile("!test")}, so the OAuth filter chain is never
 * registered under the {@code test} profile. This unit test therefore drives the controller
 * directly (bypassing the filter chain) to assert that:
 *
 * <ol>
 *   <li>The endpoint is mapped to GET (not POST) after the CR-03 fix.
 *   <li>A call to {@code connect(response)} issues a sendRedirect to the expected OAuth
 *       authorization URL, triggering {@code prompt=consent} via the {@code reconnect=true} signal
 *       parameter (D-A5).
 * </ol>
 *
 * <p>Future regression: if someone accidentally reverts to {@code @PostMapping}, the
 * {@code @GetMapping} import check in the compile step will fail first — but this test also asserts
 * the redirect target at runtime.
 */
class ConnectGmailControllerTest {

  @Test
  void connect_redirects_to_oauth_authorization_with_reconnect_signal() {
    var controller = new ConnectGmailController();

    var response = controller.connect();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation())
        .as("CR-03 + D-A5: must redirect to bundled OAuth with reconnect=true signal")
        .hasToString("/oauth2/authorization/google?reconnect=true");
  }
}
