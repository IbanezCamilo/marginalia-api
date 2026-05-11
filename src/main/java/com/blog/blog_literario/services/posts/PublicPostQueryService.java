package com.blog.blog_literario.services.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.services.images.LocalStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicPostQueryService {

    private final LocalStorageService localStorageService;
    private final PostRepository postRepository;

    //public list
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

    // public details by slug
    public PublicPostResponse getBySlug(String slug) {
        Post post = postRepository
                .findBySlugAndStatus(slug, PostStatus.PUBLISHED)
                .orElseThrow(() -> new RuntimeException("Post not Found: " + slug));

        return toResponse(post);
    }

    private PublicPostResponse toResponse(Post post) {
        User author = post.getAuthor();
        String pictureUrl = resolveProfilePicture(author);

        return new PublicPostResponse(
                post.getTitle(),
                post.getContent(),
                post.getSlug(),
                author.getId(),
                author.getName(),
                author.getDescription(),
                pictureUrl,
                post.getCategory().getName(),
                post.getCategory().getSlug(),
                post.getCoverImage(),
                post.getCreatedAt()
        );
    }

    private String resolveProfilePicture(User user){
        if(user.getProfilePicture() != null && !user.getProfilePicture().isBlank()){
            return localStorageService.buildUrl(user.getProfilePicture());
        }
        return null;
    }
}
