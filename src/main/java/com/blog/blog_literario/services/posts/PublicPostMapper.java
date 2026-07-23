package com.blog.blog_literario.services.posts;

import org.springframework.stereotype.Component;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;
import com.blog.blog_literario.services.users.PublicProfileVisibility;
import com.blog.blog_literario.utils.ReadingTime;

import lombok.RequiredArgsConstructor;

/**
 * Builds the public view of a post. Every public surface that serializes a
 * {@link PublicPostResponse} goes through here, so the author's privacy choices are
 * applied once instead of at each call site — a second mapper would silently leak
 * whatever it forgot to redact.
 */
@Component
@RequiredArgsConstructor
public class PublicPostMapper {

    private final StorageService storageService;
    private final AvatarResolver avatarResolver;

    /**
     * @param visibility the post author's public-profile choices; the author's name is
     *                   always included, the bio and photo only when they allow it
     */
    public PublicPostResponse toResponse(Post post, PublicProfileVisibility visibility) {
        User author = post.getAuthor();

        return new PublicPostResponse(
                post.getTitle(),
                post.getContent(),
                post.getSlug(),
                author.getId(),
                author.getName(),
                visibility.bioOrNull(author.getDescription()),
                avatarResolver.resolve(
                        visibility.photoOrNull(author.getProfilePicture()), author.getName()),
                post.getCategory().getName(),
                post.getCategory().getSlug(),
                storageService.buildUrl(post.getCoverImage()),
                post.getFocalX(),
                post.getFocalY(),
                post.getCreatedAt(),
                post.isFeatured(),
                ReadingTime.minutesFor(post.getWordCount())
        );
    }
}
