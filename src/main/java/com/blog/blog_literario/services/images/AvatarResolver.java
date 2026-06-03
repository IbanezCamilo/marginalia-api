package com.blog.blog_literario.services.images;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Resolves the avatar URL for a user. Returns the stored image URL when the user has
 * uploaded a profile picture; otherwise falls back to a generated UI Avatars URL
 * seeded with the user's name.
 */
@Component
@RequiredArgsConstructor
public class AvatarResolver {

    private final StorageService storageService;

    /**
     * @param fileName the stored profile picture filename, or {@code null} / blank if none
     * @param userName the user's display name (used to generate the fallback avatar)
     * @return an absolute URL for the avatar image
     */
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
