package com.blog.blog_literario.services.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.admin.AdminPostResponse;
import com.blog.blog_literario.dto.admin.AdminStatusUpdateRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.services.images.StorageService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminPostModerationService {

    private final PostRepository postRepository;
    private final StorageService storageService;

    public Page<AdminPostResponse> listAll(PostStatus status, @NonNull Pageable pageable) {
        Page<Post> posts;

        if (status != null) {
            posts = postRepository.findByStatus(status, pageable);
        } else {
            posts = postRepository.findAll(pageable);
        }
        return posts.map(this::toResponse);
    }

    //Change the post status for Global moderation
    public AdminPostResponse updateStatus(@NonNull Integer postId, AdminStatusUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + postId));

        PostStatus newStatus;
        try {
            newStatus = PostStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Estado inválido: '" + request.status() + "'. Estados válidos: DRAFT, PUBLISHED, ARCHIVED, REJECTED"
            );
        }
        post.setStatus(newStatus);
        postRepository.save(post);
        return toResponse(post);
    }

    //Hard delete of a post
    public void delete(@NonNull Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con id: " + postId));

        storageService.delete(post.getCoverImage());
        postRepository.delete(post);
    }

    //Private Helpers
    private AdminPostResponse toResponse(Post post) {
        return new AdminPostResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getStatus().name(),
                post.getStatus().getDisplayName(),
                post.getAuthor().getName(),
                post.getAuthor().getEmail(),
                post.getCategory().getName(),
                storageService.buildUrl(post.getCoverImage()),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
