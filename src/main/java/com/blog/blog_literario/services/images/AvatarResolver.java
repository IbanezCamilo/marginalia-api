package com.blog.blog_literario.services.images;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AvatarResolver {

    private final StorageService storageService;
    private String baseUrl;

    public String resolve(String fileName, String userName) {
        if (fileName != null && !fileName.isBlank()) {
            return storageService.buildUrl(fileName);
        }
        return buildAvatarUrl(userName);
    }

    private String buildAvatarUrl(String userName) {
        String encodedName = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        return "https://ui-avatars.com/api/?name=" + encodedName
                + "&background=0c0a09&color=fafaf9&bold=true&size=128&rounded=true";
    }
}
