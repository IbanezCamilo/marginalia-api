package com.blog.blog_literario.controllers.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.posts.CreatePostRequest;
import com.blog.blog_literario.dto.posts.MyPostResponse;
import com.blog.blog_literario.dto.posts.PatchStatusRequest;
import com.blog.blog_literario.dto.posts.UpdatePostRequest;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.services.posts.MyPostCommandService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Author-facing CRUD endpoints for a user's own posts, scoped to the
 * authenticated user's ID. All routes require {@code ROLE_AUTHOR} or higher
 * (enforced in {@link com.blog.blog_literario.config.SecurityConfig}).
 */
@Tag(name = "My Posts")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/posts")
public class MyPostController {

    private final MyPostCommandService myService;

    @GetMapping
    public Page<MyPostResponse> list(Authentication authentication, Pageable pageable) {
        Integer userId = getUserId(authentication);

        return myService.list(userId, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MyPostResponse> getById(Authentication authentication, @PathVariable Integer id) {
        Integer userId = getUserId(authentication);
        MyPostResponse response = myService.getById(userId, id);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<MyPostResponse> create(Authentication authentication, @Valid @RequestBody CreatePostRequest request) {
        Integer userId = getUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(myService.create(userId, request));
    }

    @PostMapping("/{id}/cover-image")
    public ResponseEntity<MyPostResponse> uploadCoverImage(Authentication authentication, @PathVariable Integer id, @RequestParam("image") MultipartFile image) {

        Integer userId = getUserId(authentication);
        MyPostResponse updated = myService.uploadCoverImage(userId, id, image);

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MyPostResponse> update(Authentication authentication, @PathVariable Integer id,
            @Valid @RequestBody UpdatePostRequest request) {
        Integer userId = getUserId(authentication);
        return ResponseEntity.ok(myService.update(userId, id, request));
    }

    /**
     * Changes the status of a post. Applies stricter transition rules than the
     * general update endpoint: only DRAFT → PUBLISHED, PUBLISHED → DRAFT,
     * and REJECTED → DRAFT are permitted for authors.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<MyPostResponse> updateStatus(Authentication authentication, @PathVariable Integer id, @Valid @RequestBody PatchStatusRequest request) {
        Integer userId = getUserId(authentication);
        MyPostResponse updated = myService.updateStatus(userId, id, request.status());

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/cover-image")
    public ResponseEntity<MyPostResponse> deleteCoverImage(@PathVariable Integer id, Authentication authentication) {
        Integer userId = getUserId(authentication);
        return ResponseEntity.ok(myService.deleteCoverImage(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id, Authentication authentication) {
        Integer userId = getUserId(authentication);
        myService.delete(userId, id);
        return ResponseEntity.noContent().build();

    }

    private Integer getUserId(Authentication authentication) {
        return ((UserDetailsImpl) authentication.getPrincipal()).getUser().getId();
    }
}
