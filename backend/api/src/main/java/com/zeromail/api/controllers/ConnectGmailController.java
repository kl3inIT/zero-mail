package com.zeromail.api.controllers;

import java.io.IOException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

@RestController
public class ConnectGmailController {

    // CR-03 fix: GET is semantically correct for an OAuth redirect trigger.
    // Safe-method semantics: the actual mutation (refresh-token rotation) happens
    // on the OAuth callback — not on this endpoint. GET requires no CSRF token
    // per Spring Security defaults (only state-changing methods need it).
    // Mirrors the inbox-zero pattern: <a href="/oauth2/authorization/google"> styled as button.
    @GetMapping("/tenant/connect-gmail")
    public void connect(HttpServletResponse res) throws IOException {
        // D-A5: reconnect path forces prompt=consent via resolver signal parameter.
        res.sendRedirect("/oauth2/authorization/google?reconnect=true");
    }
}
