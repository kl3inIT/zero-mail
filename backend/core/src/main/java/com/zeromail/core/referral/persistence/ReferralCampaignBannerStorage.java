package com.zeromail.core.referral.persistence;

import com.zeromail.core.referral.config.ReferralBannerStorageProperties;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ReferralCampaignBannerStorage {

    private final Path rootDirectory;

    public ReferralCampaignBannerStorage(ReferralBannerStorageProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.rootDirectory = properties.directory().toAbsolutePath().normalize();
    }

    public ReferralCampaignBannerStoredObject store(
            UUID campaignId, byte[] imageBytes, String contentType) {
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        Objects.requireNonNull(imageBytes, "imageBytes must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");

        String objectKey = campaignId + "/" + UUID.randomUUID() + extensionFor(contentType);
        Path targetPath = resolveObjectKey(objectKey);
        try {
            Files.createDirectories(targetPath.getParent());
            Path temporaryFilePath =
                    Files.createTempFile(targetPath.getParent(), "banner-", ".upload");
            try {
                Files.write(
                        temporaryFilePath,
                        imageBytes,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                moveIntoPlace(temporaryFilePath, targetPath);
            } finally {
                Files.deleteIfExists(temporaryFilePath);
            }
        } catch (IOException storageFailure) {
            throw new IllegalStateException(
                    "Unable to store referral campaign banner image", storageFailure);
        }
        return new ReferralCampaignBannerStoredObject(objectKey, contentType, imageBytes.length);
    }

    public Optional<byte[]> read(String objectKey) {
        Path imagePath = resolveObjectKey(objectKey);
        if (!Files.isRegularFile(imagePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(imagePath));
        } catch (IOException storageFailure) {
            throw new IllegalStateException(
                    "Unable to read referral campaign banner image", storageFailure);
        }
    }

    public void deleteIfPresent(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            Path imagePath = resolveObjectKey(objectKey);
            Files.deleteIfExists(imagePath);
            deleteDirectoryIfEmpty(imagePath.getParent());
        } catch (IOException ignoredStorageFailure) {
            // Best-effort cleanup only; stale files do not affect the active DB reference.
        }
    }

    private Path resolveObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        Path resolvedPath = rootDirectory.resolve(objectKey).normalize();
        if (!resolvedPath.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("objectKey escapes referral banner storage");
        }
        return resolvedPath;
    }

    private static void moveIntoPlace(Path temporaryFilePath, Path targetPath) throws IOException {
        try {
            Files.move(
                    temporaryFilePath,
                    targetPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupportedAtomicMove) {
            Files.move(temporaryFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteDirectoryIfEmpty(Path directoryPath) throws IOException {
        if (directoryPath == null) {
            return;
        }
        try (Stream<Path> childPaths = Files.list(directoryPath)) {
            if (childPaths.findAny().isEmpty()) {
                Files.deleteIfExists(directoryPath);
            }
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Unsupported banner image content type");
        };
    }
}
