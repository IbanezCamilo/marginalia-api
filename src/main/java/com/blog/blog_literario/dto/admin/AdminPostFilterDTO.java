package com.blog.blog_literario.dto.admin;

import java.time.LocalDateTime;

import com.blog.blog_literario.model.PostStatus;

public class AdminPostFilterDTO {

    // Required author ID filter
    private long authorId;

    // Optional status filter
    private PostStatus status;

    // Optional date range filters
    private LocalDateTime createdAfter;
    private LocalDateTime createdABefore;

    // Optional search term for title or content
    private String searchTerm;

    private Integer page = 0; // default to first page
    private Integer size = 10; // default page size

    // Default sorting by createdAt descending
    private String sort = "createdAt, desc"; // default sorting field
}
