package com.blog.blog_literario.services.users;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.dto.users.PublicAuthorResponse;
import com.blog.blog_literario.dto.users.PublicAuthorSummaryResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.posts.PublicPostMapper;

import lombok.RequiredArgsConstructor;

/**
 * Read-only service for public author profiles and their published post feeds.
 * No authentication is required to call these methods.
 *
 * <p>What a visitor sees is filtered by the author's own privacy preferences; see
 * {@link PublicProfileVisibility}. The response shape never changes — only the values.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicAuthorService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final AvatarResolver avatarResolver;
    private final PublicPostMapper publicPostMapper;
    private final AuthorVisibilityResolver authorVisibilityResolver;

    /**
     * @throws ResourceNotFoundException if no user exists with the given {@code id}
     */
    public PublicAuthorResponse getAuthorById(Integer id) {
        User author = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Autor no encontrado con ID: " + id));

        return toAuthorResponse(author, authorVisibilityResolver.forAuthor(id));
    }

    /**
     * Returns a paginated list of published posts for the given author.
     *
     * @throws ResourceNotFoundException if no user exists with the given {@code authorId}
     */
    public Page<PublicPostResponse> getPublishedPostsByAuthor(Integer authorId, Pageable pageable) {
        userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Autor no encontrado con ID: " + authorId));

        // Every post on this page has the same author, so visibility is resolved once.
        PublicProfileVisibility visibility = authorVisibilityResolver.forAuthor(authorId);

        return postRepository
                .findByAuthorIdAndStatus(authorId, PostStatus.PUBLISHED, pageable)
                .map(post -> publicPostMapper.toResponse(post, visibility));
    }

    /**
     * Authors with at least one published post, ordered by name; feeds the catalog's author facet.
     * Names are always public — they sign every post — so nothing here is redacted.
     */
    public List<PublicAuthorSummaryResponse> listPublishedAuthors() {
        return postRepository.findDistinctPublishedAuthors().stream()
                .map(author -> new PublicAuthorSummaryResponse(author.getId(), author.getName()))
                .toList();
    }

    private PublicAuthorResponse toAuthorResponse(User author, PublicProfileVisibility visibility) {
        return new PublicAuthorResponse(
                author.getId(),
                author.getName(),
                visibility.bioOrNull(author.getDescription()),
                avatarResolver.resolve(
                        visibility.photoOrNull(author.getProfilePicture()), author.getName())
        );
    }
}
