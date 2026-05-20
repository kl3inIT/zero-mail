package com.zeromail.api.controllers.settings;

import com.zeromail.api.dto.settings.CuratedCatalogResponse;
import com.zeromail.core.admin.cat.usecases.CuratedCatalogQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "settings-catalog")
@RequestMapping("/api/settings/catalog")
@PreAuthorize("isAuthenticated()")
public class SettingsCatalogController {

    private final CuratedCatalogQueryService curatedCatalogQueryService;

    public SettingsCatalogController(CuratedCatalogQueryService curatedCatalogQueryService) {
        this.curatedCatalogQueryService = curatedCatalogQueryService;
    }

    @GetMapping({"", "/"})
    public ResponseEntity<CuratedCatalogResponse> getCatalog(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        CuratedCatalogQueryService.CuratedCatalogResult curatedCatalogResult =
                curatedCatalogQueryService.getCatalog(ifNoneMatch);
        if (curatedCatalogResult.notModified()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(curatedCatalogResult.etag())
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(curatedCatalogResult.etag())
                .body(CuratedCatalogResponse.from(curatedCatalogResult));
    }
}
