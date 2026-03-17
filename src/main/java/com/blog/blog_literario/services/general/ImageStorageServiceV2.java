package com.blog.blog_literario.services.general;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorageServiceV2 {

    private final String uploadDir = "ImgTest";

    public String saveImage(MultipartFile file) {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        Path imagePath = Paths.get(uploadDir).resolve(fileName);

        try {
            Files.createDirectories(imagePath.getParent());
            Files.copy(file.getInputStream(), imagePath, StandardCopyOption.REPLACE_EXISTING);

            return fileName; //Ruta
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar la imagen", e);
        }
    }
}
