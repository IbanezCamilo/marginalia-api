package com.blog.blog_literario.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

/**
 * The image formats accepted for upload, identified by magic bytes.
 *
 * <p>Single source of truth for format detection: {@link ImageValidator} uses it to
 * accept or reject an upload, {@link FileNameGenerator} to derive the stored extension,
 * and the storage services to set the stored content type. Keeping the byte checks in
 * one place is deliberate — when the signature test was duplicated per call site, the
 * copies drifted and an incomplete WebP check silently accepted any RIFF container.
 */
public enum ImageFormat {

    JPEG(".jpg", "image/jpeg"),
    PNG(".png", "image/png"),
    WEBP(".webp", "image/webp");

    /**
     * Bytes needed to identify any supported format. WebP is the longest: its form type
     * sits at offset 8, so 12 bytes are required to tell a WebP from another RIFF file.
     */
    static final int HEADER_LENGTH = 12;

    private final String extension;
    private final String contentType;

    ImageFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    /** File extension to store the image under, including the leading dot. */
    public String extension() {
        return extension;
    }

    /** MIME type to serve the image as. Derived from the bytes, never from the client. */
    public String contentType() {
        return contentType;
    }

    /**
     * Reads the leading bytes of {@code file} and returns the format they identify, or
     * an empty Optional if they match no supported format.
     *
     * @throws IOException if the file input stream cannot be read
     */
    public static Optional<ImageFormat> detect(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return detect(is.readNBytes(HEADER_LENGTH));
        }
    }

    /**
     * Returns the format identified by {@code header}, or an empty Optional if it matches
     * none. A header shorter than a given signature can never match it, so truncated
     * files are rejected rather than matched on a partially-read buffer.
     */
    static Optional<ImageFormat> detect(byte[] header) {
        if (isJpeg(header)) {
            return Optional.of(JPEG);
        }
        if (isPng(header)) {
            return Optional.of(PNG);
        }
        if (isWebP(header)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    // JPEG: FF D8 FF
    private static boolean isJpeg(byte[] h) {
        return h.length >= 3
                && h[0] == (byte) 0xFF
                && h[1] == (byte) 0xD8
                && h[2] == (byte) 0xFF;
    }

    // PNG: 89 50 4E 47 ("\x89PNG")
    private static boolean isPng(byte[] h) {
        return h.length >= 4
                && h[0] == (byte) 0x89
                && h[1] == (byte) 0x50  // P
                && h[2] == (byte) 0x4E  // N
                && h[3] == (byte) 0x47; // G
    }

    // WebP: "RIFF" at offset 0, a 4-byte little-endian size, then "WEBP" at offset 8.
    // Both halves matter — "RIFF" alone also matches AVI, WAV and every other RIFF
    // container, so checking only the first four bytes accepts arbitrary non-image data.
    private static boolean isWebP(byte[] h) {
        return h.length >= HEADER_LENGTH
                && h[0] == (byte) 0x52   // R
                && h[1] == (byte) 0x49   // I
                && h[2] == (byte) 0x46   // F
                && h[3] == (byte) 0x46   // F
                && h[8] == (byte) 0x57   // W
                && h[9] == (byte) 0x45   // E
                && h[10] == (byte) 0x42  // B
                && h[11] == (byte) 0x50; // P
    }
}
