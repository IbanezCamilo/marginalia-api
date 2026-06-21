package com.blog.blog_literario.services.images;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.config.properties.AppProperties;
import com.blog.blog_literario.config.properties.StorageProperties;
import com.blog.blog_literario.utils.FileNameGenerator;
import com.blog.blog_literario.utils.ImageValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Disk-backed implementation of {@link StorageService}.
 *
 * <p>Files are written under the directory configured by {@code storage.local.upload-dir}
 * (defaults to {@code uploads/}). The upload path is normalized and validated before
 * every write to prevent path traversal attacks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final AppProperties appProperties;
    private final StorageProperties storageProperties;

    /**
     * Validates the file, removes the previous image if present, generates a unique
     * name from the file's magic bytes, and writes it to the upload directory.
     *
     * @throws IllegalArgumentException if the file fails size or format validation
     * @throws RuntimeException         if the file cannot be written to disk
     */
    @Override
    public String save(MultipartFile file, String previousFile) {
        try {

            ImageValidator.validate(file);

            if (previousFile != null && !previousFile.isBlank()) {
                delete(previousFile);
            }

            String fileName = FileNameGenerator.generate(file);

            Path uploadPath = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            Path destination = uploadPath.resolve(fileName);

            // Ensure the destination path is within the upload directory to prevent path traversal
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("Image saved successfully: {}", fileName);

            return fileName;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            log.error("Error saving image to disk", e);
            throw new RuntimeException("No se pudo guardar la imagen. Inténtalo de nuevo.", e);
        }
    }

    /**
     * Deletes the file identified by {@code fileName} from the upload directory.
     * Silently skips the operation if the file does not exist or the name is blank.
     * Logs a warning and returns without throwing if the path escapes the upload directory.
     */
    @Override
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        try {
            Path uploadPath = Paths.get(storageProperties.uploadDir()).normalize();
            Path filePath = uploadPath.resolve(fileName).normalize();

            if (!filePath.startsWith(uploadPath)) {
                log.warn("Attempted access to file outside the allowed directory: " + fileName);
                return;
            }

            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                log.info("Image deleted: {}", fileName);
            } else {
                log.warn("Attempted to delete an image that does not exist: {}", fileName);
            }
        } catch (IOException e) {
            log.error("Failed to delete image: {}", fileName, e);
        }
    }

    @Override
    public String buildUrl(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        return appProperties.baseUrl() + "/api/images/" + fileName;
    }

}
