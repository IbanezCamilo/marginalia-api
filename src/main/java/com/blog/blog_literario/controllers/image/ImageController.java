package com.blog.blog_literario.controllers.image;

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
import org.springframework.beans.factory.annotation.Value;

import com.blog.blog_literario.exception.ResourceNotFoundException;

/**
 * Serves locally stored images. The upload directory is read from
 * {@code storage.local.upload-dir} (defaults to {@code uploads/}).
 *
 * <p>Path traversal is prevented by verifying that the resolved file path
 * stays within the upload directory before serving the resource.
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    @Value("${storage.local.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Returns the raw image bytes with the correct {@code Content-Type}.
     * Rejects requests whose filename would escape the upload directory.
     *
     * @throws ResourceNotFoundException if the file does not exist or is not readable
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = uploadPath.resolve(filename).normalize();

            if (!filePath.startsWith(uploadPath)) {
                throw new RuntimeException("Acceso no permitido");
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Image not found: " + filename);
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (Exception e) {
            throw new RuntimeException("Error retrieving image: " + filename, e);
        }
    }

}
