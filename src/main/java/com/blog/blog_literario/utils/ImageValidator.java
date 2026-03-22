package com.blog.blog_literario.utils;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;

public class ImageValidator {
    
    //Max size 5MB
    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    //Validates that the file is not empty, does not exceed max size, and has valid image magic bytes
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

    
    //Validates the magic bytes of the file to ensure it's a valid image format (JPEG, PNG, WebP)
    private static void validateMagicBytes(MultipartFile file) throws IOException {
        byte[] header = new byte[8];

        try(InputStream is = file.getInputStream()){
            int bytesRead = is.read(header);
            if(bytesRead < 4){
                throw new IllegalArgumentException("Archivo demasiado pequeño para ser una imagen valida");
            }
        }

        if(!isJpeg(header) && !isPng(header) && !isWebP(header)){
            throw new IllegalArgumentException("Formato no es un formato de imagen válido (JPEG, PNG, WebP)");
        }
    }

    //JPEG: starts with FF D8 FF
    private static boolean isJpeg(byte[] h){
        return  h[0] == (byte) 0xFF &&
                h[1] == (byte) 0xD8 &&
                h[2] == (byte) 0xFF;
    }

    //PNG: starts with 89 50 4E 47 0D 0A 1A 0A
    private static boolean isPng(byte[] h){
        return  h[0] == (byte) 0x89 &&
                h[1] == (byte) 0x50 && //P
                h[2] == (byte) 0x4E && //N
                h[3] == (byte) 0x47;   //G
    }

    //WebP: starts with 52 49 46 46 (RIFF) and then 57 45 42 50 (WEBP) at offset 8
    private static boolean isWebP(byte[] h) {
        return h[0] == (byte) 0x52 && // 'R'
            h[1] == (byte) 0x49 && // 'I'
            h[2] == (byte) 0x46 && // 'F'
            h[3] == (byte) 0x46;   // 'F'
}
}
