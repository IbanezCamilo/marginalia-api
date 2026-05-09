package com.blog.blog_literario.controllers.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.services.posts.PublicPostQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public/posts")
@RequiredArgsConstructor
public class PublicPostController {

    private final PublicPostQueryService publicPostQueryService;

    @GetMapping
    public Page<PublicPostResponse> list(
            @RequestParam(required = false) Integer categoryId,
            @PageableDefault(size = 10, sort = "publishedAt",
            direction = Sort.Direction.DESC) Pageable pageable) {
        return publicPostQueryService.listPublishedPosts(categoryId, pageable);
    }

    @GetMapping("/{slug}")
    public PublicPostResponse detail(@PathVariable String slug) {
        return publicPostQueryService.getBySlug(slug);
    }
}
