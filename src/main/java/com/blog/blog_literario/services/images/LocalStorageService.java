package com.blog.blog_literario.services.images;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.config.properties.AppProperties;
import com.blog.blog_literario.utils.FileNameGenerator;
import com.blog.blog_literario.utils.ImageValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    @Value("${storage.local.upload-dir:uploads}")
    private String uploadDir;

    private final AppProperties appProperties;

    @Override
    public String save(MultipartFile file, String previousFile) {
        try {

            //Validate the file (size, type, etc.)
            ImageValidator.validate(file);

            //Clean up old file if exists
            if (previousFile != null && !previousFile.isBlank()) {
                delete(previousFile);
            }

            //Generate a unique file name based on the file's content (magic bytes) and original name
            String fileName = FileNameGenerator.generate(file);

            // Ensure the upload directory exists and save the file
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            Path destination = uploadPath.resolve(fileName);

            // Ensure the destination path is within the upload directory to prevent path traversal
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("Image saved successfully: {}", fileName);

            return fileName;
        } catch (IllegalArgumentException e) {
            // Validation errors (e.g., invalid file type, size too large)
            throw e;
        } catch (IOException e) {
            log.error("Error saving image to disk", e);
            throw new RuntimeException("No se pudo guardar la imagen. Inténtalo de nuevo.", e);
        }
    }

    @Override
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        try {
            Path uploadPath = Paths.get(uploadDir).normalize();
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
