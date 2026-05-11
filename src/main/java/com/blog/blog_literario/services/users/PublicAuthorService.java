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
import com.blog.blog_literario.services.images.LocalStorageService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicAuthorService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final LocalStorageService localStorageService;

    public PublicAuthorResponse getAuthorById(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Autor no encontrado con ID: " + id));

        return toAuthorResponse(user);
    }

    public Page<PublicPostResponse> getPublishedPostsByAuthor(Integer authorId, Pageable pageable) {
        userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Autor no encontrado con ID: " + authorId));

        return postRepository
                .findByAuthorIdAndStatus(authorId, PostStatus.PUBLISHED, pageable)
                .map(this::toPostResponse);
    }

    private PublicAuthorResponse toAuthorResponse(User user) {
        String pictureUrl = resolveProfilePicture(user);
        return new PublicAuthorResponse(
                user.getId(),
                user.getName(),
                user.getDescription(),
                pictureUrl
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
                resolveProfilePicture(author),
                post.getCategory().getName(),
                post.getCategory().getSlug(),
                post.getCoverImage(),
                post.getCreatedAt()
        );
    }

    private String resolveProfilePicture(User user) {
        if (user.getProfilePicture() != null && !user.getProfilePicture().isBlank()) {
            return localStorageService.buildUrl(user.getProfilePicture());
        }
        return null;
    }
}