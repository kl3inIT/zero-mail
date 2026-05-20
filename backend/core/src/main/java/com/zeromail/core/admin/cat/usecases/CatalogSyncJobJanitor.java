package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.cat.persistence.lowlevel.CatalogSyncJobRepository;
import java.time.Duration;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CatalogSyncJobJanitor {

    private static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final CatalogSyncJobRepository catalogSyncJobRepository;

    public CatalogSyncJobJanitor(CatalogSyncJobRepository catalogSyncJobRepository) {
        this.catalogSyncJobRepository =
                Objects.requireNonNull(catalogSyncJobRepository, "catalogSyncJobRepository");
    }

    @Scheduled(fixedDelay = 60000)
    public void abandonStaleJobs() {
        catalogSyncJobRepository.abandonStaleProcessingJobs(STALE_AFTER);
    }
}
