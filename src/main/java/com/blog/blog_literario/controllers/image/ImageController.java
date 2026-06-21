package com.blog.blog_literario.controllers.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.config.properties.StorageProperties;
import com.blog.blog_literario.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Serves locally stored images. The upload directory is read from
 * {@code storage.local.upload-dir} (defaults to {@code uploads/}).
 *
 * <p>Path traversal is prevented by verifying that the resolved file path
 * stays within the upload directory before serving the resource.
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final StorageProperties storageProperties;

    /**
     * Returns the raw image bytes with the correct {@code Content-Type}.
     * Rejects requests whose filename would escape the upload directory.
     *
     * @throws ResourceNotFoundException if the file does not exist or is not readable
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        Path uploadPath = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(filename).normalize();

        // Don't reveal that a path-traversal attempt was detected — report it the
        // same way as a missing file.
        if (!filePath.startsWith(uploadPath)) {
            throw new ResourceNotFoundException("Image not found: " + filename);
        }

        Resource resource;
        String contentType;
        try {
            resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Image not found: " + filename);
            }

            contentType = Files.probeContentType(filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Error retrieving image: " + filename, e);
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

}
