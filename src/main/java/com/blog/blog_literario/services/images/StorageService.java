package com.blog.blog_literario.services.images;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /*
    *@param file
    *@param previusFile
    *return String (Path)
    */
    String save(MultipartFile file, String previusFile);

    void delete(String fiileName);

    String buildUrl(String fileName);
}
