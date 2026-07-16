package com.blog.blog_literario.services.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.dto.users.PublicAuthorResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;

import lombok.RequiredArgsConstructor;

/**
 * Read-only service for public author profiles and their published post feeds.
 * No authentication is required to call these methods.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicAuthorService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final StorageService storageService;
    private final AvatarResolver avatarResolver;

    /**
     * @throws ResourceNotFoundException if no user exists with the given {@code id}
     */
    public PublicAuthorResponse getAuthorById(Integer id) {
        User author = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Autor no encontrado con ID: " + id));

        return toAuthorResponse(author);
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

        return postRepository
                .findByAuthorIdAndStatus(authorId, PostStatus.PUBLISHED, pageable)
                .map(this::toPostResponse);
    }

    private PublicAuthorResponse toAuthorResponse(User author) {
        return new PublicAuthorResponse(
                author.getId(),
                author.getName(),
                author.getDescription(),
                avatarResolver.resolve(author.getProfilePicture(), author.getName())
        );
    }

    private PublicPostResponse toPostResponse(Post post) {
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
