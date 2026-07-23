package com.blog.blog_literario.services.posts;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.posts.PostCatalogFilter;
import com.blog.blog_literario.dto.posts.PostCatalogSort;
import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.repositories.PostCatalogSpecifications;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.services.users.AuthorVisibilityResolver;
import com.blog.blog_literario.services.users.PublicProfileVisibility;

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
    private final PublicPostMapper publicPostMapper;
    private final AuthorVisibilityResolver authorVisibilityResolver;

    /**
     * Returns a paginated page of published posts constrained by every active facet in
     * {@code filter}, ordered by {@code catalogSort}. Absent facets (nulls) don't
     * constrain the query — {@link Specification#allOf} skips null specifications.
     * Any sort carried by {@code pageable} is discarded; only whitelisted
     * {@link PostCatalogSort} orderings reach the query.
     */
    public Page<PublicPostResponse> listPublishedPosts(
            PostCatalogFilter filter, PostCatalogSort catalogSort, Pageable pageable) {
        Pageable effective = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), catalogSort.toSort());
        Specification<Post> spec = Specification.allOf(
                PostCatalogSpecifications.isPublished(),
                PostCatalogSpecifications.hasCategorySlug(filter.categorySlug()),
                PostCatalogSpecifications.hasCategory(filter.categoryId()),
                PostCatalogSpecifications.hasAuthor(filter.authorId()),
                PostCatalogSpecifications.readingTimeIn(filter.time()),
                PostCatalogSpecifications.matchesQuery(filter.q()));

        Page<Post> posts = postRepository.findAll(spec, effective);

        // One visibility lookup for every distinct author on the page, not one per post.
        Map<Integer, PublicProfileVisibility> visibility =
                authorVisibilityResolver.forAuthors(distinctAuthorIds(posts));

        return posts.map(post -> publicPostMapper.toResponse(post, visibilityFor(visibility, post)));
    }

    /**
     * @throws ResourceNotFoundException if no published post exists with the given {@code slug}
     */
    public PublicPostResponse getBySlug(String slug) {
        Post post = postRepository
                .findBySlugAndStatus(slug, PostStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + slug));

        return publicPostMapper.toResponse(
                post, authorVisibilityResolver.forAuthor(post.getAuthor().getId()));
    }

    private Set<Integer> distinctAuthorIds(Page<Post> posts) {
        Set<Integer> authorIds = new LinkedHashSet<>();
        for (Post post : posts) {
            authorIds.add(post.getAuthor().getId());
        }
        return authorIds;
    }

    /** Falls back to fully visible so a missing entry can never blank out a byline. */
    private PublicProfileVisibility visibilityFor(
            Map<Integer, PublicProfileVisibility> visibility, Post post) {
        return visibility.getOrDefault(post.getAuthor().getId(), PublicProfileVisibility.VISIBLE);
    }
}
