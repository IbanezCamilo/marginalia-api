package com.blog.blog_literario.services.images;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction for persisting and retrieving uploaded image files.
 * The default implementation stores files on the local filesystem ({@link LocalStorageService}).
 */
public interface StorageService {

    /**
     * Validates, stores, and replaces a user-uploaded image.
     *
     * @param file         the uploaded image (must pass {@link com.blog.blog_literario.utils.ImageValidator} checks)
     * @param previousFile file name of the image to delete before saving, or {@code null} if none
     * @return the generated file name of the newly saved image
     */
    String save(MultipartFile file, String previusFile);

    void delete(String fileName);

    String buildUrl(String fileName);

}
