package com.blog.blog_literario.controllers.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.dto.users.PublicAuthorResponse;
import com.blog.blog_literario.services.users.PublicAuthorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public/authors")
@RequiredArgsConstructor
public class PublicAuthorController {

    private final PublicAuthorService publicAuthorService;

    /**
     * GET /api/public/authors/{id}
     * Returns public profile of an author.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PublicAuthorResponse> getAuthor(@PathVariable Integer id) {
        return ResponseEntity.ok(publicAuthorService.getAuthorById(id));
    }

    /**
     * GET /api/public/authors/{id}/posts
     * Returns published posts by a specific author.
     */
    @GetMapping("/{id}/posts")
    public Page<PublicPostResponse> getAuthorPosts(
            @PathVariable Integer id,
            @PageableDefault(size = 12, sort = "publishedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return publicAuthorService.getPublishedPostsByAuthor(id, pageable);
    }
}