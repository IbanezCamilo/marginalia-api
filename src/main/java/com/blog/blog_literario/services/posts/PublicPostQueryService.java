package com.blog.blog_literario.services.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.repositories.PostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicPostQueryService {

    public static final String PUBLISHED = "PUBLISHED";

    private final PostRepository postRepository;

    //public list
    public Page<PublicPostResponse> listPublishedPosts(Pageable pageable) {

        return postRepository
                .findByStatus(PostStatus.PUBLISHED, pageable)
                .map(this::toResponse);
    }

    // public details by slug
    public PublicPostResponse getBySlug(String slug) {
        Post post = postRepository
                .findBySlugAndStatus(slug, PostStatus.PUBLISHED)
                .orElseThrow(() -> new RuntimeException("Post not Found: " + slug));

        return toResponse(post);
    }

    private PublicPostResponse toResponse(Post post) {
        return new PublicPostResponse(
                post.getTitle(),
                post.getSlug(),
                post.getContent(),
                post.getAuthor().getName(),
                post.getCategory().getName(),
                post.getCreatedAt()
        );
    }
}
