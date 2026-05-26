package com.zeromail.api.controllers.llm;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Deprecated(forRemoval = true)
@RestController
@Tag(name = "llm-byok")
@RequestMapping("/api/llm/byok")
public class ByokController {

    private static final URI NEW_BYOK_LOCATION = URI.create("/api/byok");
    private static final ByokMovedResponse MOVED_RESPONSE =
            new ByokMovedResponse("ai.byok.moved", "Use /api/byok instead");

    @RequestMapping(
            path = {"", "/", "/validate", "/**"},
            method = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.PATCH,
                RequestMethod.DELETE
            })
    public ResponseEntity<ByokMovedResponse> moved() {
        return ResponseEntity.status(HttpStatus.GONE)
                .header(HttpHeaders.LOCATION, NEW_BYOK_LOCATION.toString())
                .body(MOVED_RESPONSE);
    }

    public record ByokMovedResponse(String code, String message) {}
}
