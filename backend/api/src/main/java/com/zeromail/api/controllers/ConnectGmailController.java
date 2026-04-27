package com.zeromail.api.controllers;

import java.io.IOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

@RestController
public class ConnectGmailController {

    // INFO-8: POST verb locked — frontend ReconnectPrompt uses <form method="post">.
    @PostMapping("/tenant/connect-gmail")
    public void connect(HttpServletResponse res) throws IOException {
        // D-A5: reconnect path forces prompt=consent via resolver signal parameter.
        res.sendRedirect("/oauth2/authorization/google?reconnect=true");
    }
}
