package com.blog.blog_literario.controllers.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.posts.PostCatalogFilter;
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
     * Returns a paginated feed of published posts filtered by any combination of
     * stacked facets: {@code category} (slug), {@code categoryId} (legacy), {@code authorId},
     * {@code time} (short|medium|long over the persisted word count), and {@code q}
     * (title + author name search). Unknown facet values are ignored, never an error —
     * these parameters live in shareable URLs.
     */
    @GetMapping
    public Page<PublicPostResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) String time,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @PageableDefault(size = 10) Pageable pageable) {
        return publicPostQueryService.listPublishedPosts(
                PostCatalogFilter.of(category, categoryId, authorId, time, q),
                PostCatalogSort.from(sort), pageable);
    }

    @GetMapping("/{slug}")
    public PublicPostResponse detail(@PathVariable String slug) {
        return publicPostQueryService.getBySlug(slug);
    }
}
