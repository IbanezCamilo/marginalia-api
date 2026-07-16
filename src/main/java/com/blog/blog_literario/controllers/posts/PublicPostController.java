package com.blog.blog_literario.controllers.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.posts.PostCatalogSort;
import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.services.posts.PublicPostQueryService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Public read-only endpoints for the post feed. No authentication required.
 * Only PUBLISHED posts are returned.
 */
@Tag(name = "Public Posts")
@RestController
@RequestMapping("/api/public/posts")
@RequiredArgsConstructor
public class PublicPostController {

    private final PublicPostQueryService publicPostQueryService;

    /**
     * Returns a paginated feed of published posts, optionally filtered by {@code categoryId}.
     * Ordering is restricted to the named {@link PostCatalogSort} keys; unknown or missing
     * {@code sort} values fall back to the featured-first default.
     */
    @GetMapping
    public Page<PublicPostResponse> list(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String sort,
            @PageableDefault(size = 10) Pageable pageable) {
        return publicPostQueryService.listPublishedPosts(
                categoryId, PostCatalogSort.from(sort), pageable);
    }

    @GetMapping("/{slug}")
    public PublicPostResponse detail(@PathVariable String slug) {
        return publicPostQueryService.getBySlug(slug);
    }
}
