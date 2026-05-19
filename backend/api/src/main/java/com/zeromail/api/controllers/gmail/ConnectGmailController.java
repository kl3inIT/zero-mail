package com.zeromail.api.controllers.gmail;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectGmailController {

    // GET is semantically correct for an OAuth redirect trigger.
    // The actual mutation (refresh-token rotation) happens on the OAuth callback,
    // not on this endpoint. GET requires no CSRF token per Spring Security defaults.
    @GetMapping("/api/tenant/connect-gmail")
    public ResponseEntity<Void> connect() {
        // Reconnect path forces prompt=consent via resolver signal parameter.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/google?reconnect=true"))
                .build();
    }
}
