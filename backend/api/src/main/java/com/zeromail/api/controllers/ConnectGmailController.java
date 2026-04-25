package com.zeromail.api.controllers;

import java.io.IOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

@RestController
public class ConnectGmailController {

    @PostMapping("/tenant/connect-gmail")
    public void connect(HttpServletResponse res) throws IOException {
        res.sendRedirect("/oauth2/authorization/google-gmail");
    }
}
