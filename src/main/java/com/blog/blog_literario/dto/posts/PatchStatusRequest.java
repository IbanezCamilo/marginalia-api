package com.blog.blog_literario.dto.posts;

// This DTO is specifically for changing the status of a post, it only contains the status field
public record PatchStatusRequest(String status) {

}
