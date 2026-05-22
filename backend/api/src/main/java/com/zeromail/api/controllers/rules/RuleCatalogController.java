package com.zeromail.api.controllers.rules;

import com.zeromail.api.dto.rules.catalog.RuleCatalogActionsResponse;
import com.zeromail.api.dto.rules.catalog.RuleCatalogExamplesResponse;
import com.zeromail.core.rules.catalog.domain.RuleCatalogLocale;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "rules")
@RequestMapping("/api/rules/catalog")
public class RuleCatalogController {

    private final RuleCatalogUserService ruleCatalogUserService;

    public RuleCatalogController(RuleCatalogUserService ruleCatalogUserService) {
        this.ruleCatalogUserService =
                Objects.requireNonNull(ruleCatalogUserService, "ruleCatalogUserService");
    }

    @GetMapping("/examples")
    public RuleCatalogExamplesResponse examples(
            @RequestParam(name = "locale", defaultValue = "en") String locale) {
        return RuleCatalogExamplesResponse.from(
                ruleCatalogUserService.listExamplePersonas(parseLocale(locale)));
    }

    @GetMapping("/actions")
    public RuleCatalogActionsResponse actions(
            @RequestParam(name = "locale", defaultValue = "en") String locale) {
        return RuleCatalogActionsResponse.from(
                ruleCatalogUserService.listActionDescriptors(parseLocale(locale)));
    }

    private static RuleCatalogLocale parseLocale(String locale) {
        try {
            return RuleCatalogLocale.fromId(locale);
        } catch (NoSuchElementException unknownLocale) {
            throw new InvalidRuleCatalogLocaleException();
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private static final class InvalidRuleCatalogLocaleException extends ResponseStatusException {

        private InvalidRuleCatalogLocaleException() {
            super(HttpStatus.BAD_REQUEST, "Unsupported rule catalog locale");
        }
    }
}
