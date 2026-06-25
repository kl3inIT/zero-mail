package com.zeromail.core.referral.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zero-mail.referral.banner-storage")
public record ReferralBannerStorageProperties(Path directory) {

    private static final Path DEFAULT_DIRECTORY = Paths.get("var", "referral-banners");

    public ReferralBannerStorageProperties {
        directory = directory == null ? DEFAULT_DIRECTORY : directory;
        directory = directory.toAbsolutePath().normalize();
    }
}
