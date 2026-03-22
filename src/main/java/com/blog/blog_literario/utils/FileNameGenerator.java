package com.blog.blog_literario.utils;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class FileNameGenerator {

    //Detects the file extension based on the magic bytes of the file
    public static String generate(MultipartFile file) throws IOException {
        String extension = detectExtension(file);
        return UUID.randomUUID().toString() + extension;
    }

    private static String detectExtension(MultipartFile file) throws IOException {
        byte[] header = new byte[4];

        try (InputStream is = file.getInputStream()) {
            is.read(header);
        }

        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return ".jpg";
        }

        // PNG: 89 50 4E 47
        if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 &&
            header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
            return ".png";
        }

        // WebP: 52 49 46 46
        if (header[0] == (byte) 0x52 && header[1] == (byte) 0x49 &&
            header[2] == (byte) 0x46 && header[3] == (byte) 0x46) {
            return ".webp";
        }

        // If we reach here, the format is not recognized
        throw new IllegalStateException("Formato no reconocido. Asegúrate de llamar ImageValidator.validate() primero");
    }
}
