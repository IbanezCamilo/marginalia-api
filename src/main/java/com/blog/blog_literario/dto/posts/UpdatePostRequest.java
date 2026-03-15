package com.blog.blog_literario.dto.posts;
//This DTO is used for updating a post, it contains all the fields that can be updated, but all of them are optional, so the client can choose which fields to update without needing to send all of them

public record UpdatePostRequest(
        String title,
        String content,
        Integer categoryId,
        String status
        ) {

}
