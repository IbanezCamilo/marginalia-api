package com.blog.blog_literario.services.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.posts.PostCatalogSort;
import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;

import lombok.RequiredArgsConstructor;

/**
 * Read-only service for the public post feed. Only PUBLISHED posts are exposed;
 * no authentication is required to call these methods.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicPostQueryService {

    private final PostRepository postRepository;
    private final StorageService storageService;
    private final AvatarResolver avatarResolver;

    /**
     * Returns a paginated page of published posts ordered by {@code catalogSort},
     * optionally filtered by {@code categoryId}. Any sort carried by {@code pageable}
     * is discarded — only whitelisted {@link PostCatalogSort} orderings reach the query.
     */
    public Page<PublicPostResponse> listPublishedPosts(
            Integer categoryId, PostCatalogSort catalogSort, Pageable pageable) {
        Pageable effective = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), catalogSort.toSort());
        return listPublishedPosts(categoryId, effective);
    }

    /**
     * Returns a paginated page of published posts, optionally filtered by {@code categoryId}.
     * Passing {@code null} returns posts from all categories.
     */
    public Page<PublicPostResponse> listPublishedPosts(Integer categoryId, Pageable pageable) {
        if (categoryId != null) {
            return postRepository
                    .findByCategoryIdAndStatus(categoryId, PostStatus.PUBLISHED, pageable)
                    .map(this::toResponse);
        }
        return postRepository
                .findByStatus(PostStatus.PUBLISHED, pageable)
                .map(this::toResponse);
    }

    // Overload for backward compatibility (no categoryId filter)
    public Page<PublicPostResponse> listPublishedPosts(Pageable pageable) {
        return listPublishedPosts(null, pageable);
    }

    /**
     * @throws ResourceNotFoundException if no published post exists with the given {@code slug}
     */
    public PublicPostResponse getBySlug(String slug) {
        Post post = postRepository
                .findBySlugAndStatus(slug, PostStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + slug));

        return toResponse(post);
    }

    private PublicPostResponse toResponse(Post post) {
        User author = post.getAuthor();

        return new PublicPostResponse(
                post.getTitle(),
                post.getContent(),
                post.getSlug(),
                author.getId(),
                author.getName(),
                author.getDescription(),
                avatarResolver.resolve(author.getProfilePicture(), author.getName()),
                post.getCategory().getName(),
                post.getCategory().getSlug(),
                storageService.buildUrl(post.getCoverImage()),
                post.getFocalX(),
                post.getFocalY(),
                post.getCreatedAt(),
                post.isFeatured()
        );
    }
}
