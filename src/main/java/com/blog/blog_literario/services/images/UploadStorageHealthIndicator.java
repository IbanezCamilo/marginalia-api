package com.blog.blog_literario.services.images;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.blog.blog_literario.config.properties.StorageProperties;

import lombok.RequiredArgsConstructor;

/**
 * Reports {@code /actuator/health} as DOWN if the local upload directory becomes
 * unwritable or free disk space drops below {@code storage.health.min-free-space-mb},
 * instead of staying silently UP until a user fails to upload an image.
 */
@Component
@ConditionalOnProperty(name = "storage.active", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class UploadStorageHealthIndicator implements HealthIndicator {

    private final StorageProperties storageProperties;

    @Value("${storage.health.min-free-space-mb:100}")
    private long minFreeSpaceMb;

    @Override
    public Health health() {
        Path uploadPath = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            return Health.down()
                    .withDetail("reason", "upload directory could not be created")
                    .withDetail("path", uploadPath.toString())
                    .withException(e)
                    .build();
        }

        if (!Files.isWritable(uploadPath)) {
            return Health.down()
                    .withDetail("reason", "upload directory is not writable")
                    .withDetail("path", uploadPath.toString())
                    .build();
        }

        long freeSpaceMb = uploadPath.toFile().getUsableSpace() / (1024 * 1024);
        if (freeSpaceMb < minFreeSpaceMb) {
            return Health.down()
                    .withDetail("reason", "low disk space")
                    .withDetail("path", uploadPath.toString())
                    .withDetail("freeSpaceMb", freeSpaceMb)
                    .withDetail("thresholdMb", minFreeSpaceMb)
                    .build();
        }

        return Health.up()
                .withDetail("path", uploadPath.toString())
                .withDetail("freeSpaceMb", freeSpaceMb)
                .build();
    }
}
