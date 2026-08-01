package com.blog.blog_literario.utils;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;

/**
 * Validates uploaded image files by checking file size and magic-byte format.
 *
 * <p>Supported formats: JPEG, PNG, WebP. Maximum allowed size: 5 MB.
 * Must be called before {@link FileNameGenerator} to ensure the extension can be
 * derived from verified magic bytes.
 */
public class ImageValidator {

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    /**
     * Validates that {@code file} is non-empty, within the 5 MB size limit, and has
     * magic bytes matching a supported image format (JPEG, PNG, WebP).
     *
     * @throws IllegalArgumentException if any validation check fails
     * @throws IOException              if the file input stream cannot be read
     */
    public static void validate(MultipartFile file) throws IOException {
        validateNotEmpty(file);
        validateSize(file);
        validateMagicBytes(file);
    }

    private static void validateNotEmpty(MultipartFile file){
        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException("No se propocionó ningún archivo");
        }
    }

    private static void validateSize(MultipartFile file){
        if(file.getSize() > MAX_SIZE_BYTES){
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido 5MB. " + "Tamaño recibido: " + (file.getSize() / 1024 / 1024) + "MB");
        }
    }

    /**
     * Reads the leading bytes of the file and checks them against the signatures in
     * {@link ImageFormat}.
     *
     * @throws IllegalArgumentException if the bytes do not match JPEG, PNG, or WebP
     */
    private static void validateMagicBytes(MultipartFile file) throws IOException {
        byte[] header;

        try (InputStream is = file.getInputStream()) {
            header = is.readNBytes(ImageFormat.HEADER_LENGTH);
        }

        // Keeps the original 4-byte floor: anything shorter cannot be a real image even
        // if it happens to carry a 3-byte JPEG signature. Reported separately from an
        // unrecognized format so the message stays useful.
        if (header.length < 4) {
            throw new IllegalArgumentException("Archivo demasiado pequeño para ser una imagen valida");
        }

        if (ImageFormat.detect(header).isEmpty()) {
            throw new IllegalArgumentException("Formato no es un formato de imagen válido (JPEG, PNG, WebP)");
        }
    }
}
