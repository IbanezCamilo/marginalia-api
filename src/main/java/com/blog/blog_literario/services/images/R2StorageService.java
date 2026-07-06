package com.blog.blog_literario.services.images;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.config.properties.R2Properties;
import com.blog.blog_literario.utils.FileNameGenerator;
import com.blog.blog_literario.utils.ImageValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Cloudflare R2 implementation of {@link StorageService}, active only when
 * {@code storage.active=r2}.
 *
 * <p>Objects are stored under content-unique keys ({@link FileNameGenerator}) and served
 * from a custom domain via {@link #buildUrl(String)}, so the local {@code /api/images/**}
 * endpoint is bypassed in this mode.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.active", havingValue = "r2")
@RequiredArgsConstructor
public class R2StorageService implements StorageService {

    private final S3Client r2Client;
    private final R2Properties r2Properties;

    /**
     * Validates the file, uploads it to R2, and — only once the upload succeeds — deletes
     * the previous image. Uploading before deleting means a failed upload never leaves the
     * user without their existing image.
     *
     * @throws IllegalArgumentException if the file fails size or format validation
     * @throws RuntimeException         if the object cannot be uploaded to R2
     */
    @Override
    public String save(MultipartFile file, String previousFile) {
        try {
            ImageValidator.validate(file);

            String fileName = FileNameGenerator.generate(file);

            r2Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(r2Properties.bucketName())
                            .key(fileName)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));

            log.info("Image uploaded to R2: {}", fileName);

            // Only delete the previous file once the new upload succeeded.
            if (previousFile != null && !previousFile.isBlank()) {
                delete(previousFile);
            }

            return fileName;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | S3Exception e) {
            log.error("Error uploading image to R2", e);
            throw new RuntimeException("No se pudo guardar la imagen. Inténtalo de nuevo.", e);
        }
    }

    /**
     * Deletes the object identified by {@code fileName}. A missing key is logged as a
     * warning (the desired end state already holds); other R2 errors are logged without
     * throwing so a failed delete never breaks the calling flow.
     */
    @Override
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        try {
            r2Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(r2Properties.bucketName())
                    .key(fileName)
                    .build());
            log.info("Image deleted from R2: {}", fileName);
        } catch (NoSuchKeyException e) {
            log.warn("Key no longer exists in R2: {}", fileName);
        } catch (S3Exception e) {
            log.error("Error deleting {} from R2", fileName, e);
        }
    }

    @Override
    public String buildUrl(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        return r2Properties.publicBaseUrl() + "/" + fileName;
    }
}
