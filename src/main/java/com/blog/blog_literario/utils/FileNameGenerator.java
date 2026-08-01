package com.blog.blog_literario.utils;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

/**
 * Generates collision-free file names for uploaded images by combining a random UUID
 * with an extension derived from the file's magic bytes.
 *
 * <p>{@link ImageValidator#validate} must be called before {@link #generate} so that
 * an unrecognized format throws at validation time, not here.
 */
public class FileNameGenerator {

    /**
     * Returns a unique file name (UUID + extension) for the given upload.
     *
     * @throws IOException              if the file input stream cannot be read
     * @throws IllegalStateException    if the magic bytes do not match a known format
     *                                  (indicates {@link ImageValidator} was skipped)
     */
    public static String generate(MultipartFile file) throws IOException {
        String extension = detectExtension(file);
        return UUID.randomUUID().toString() + extension;
    }

    private static String detectExtension(MultipartFile file) throws IOException {
        return ImageFormat.detect(file)
                .map(ImageFormat::extension)
                .orElseThrow(() -> new IllegalStateException(
                        "Formato no reconocido. Asegúrate de llamar ImageValidator.validate() primero"));
    }
}
