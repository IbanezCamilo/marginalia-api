package com.blog.blog_literario.services.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.blog.blog_literario.dto.posts.PostCatalogSort;
import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;

@ExtendWith(MockitoExtension.class)
class PublicPostQueryServiceTest {

    @Mock PostRepository postRepository;
    @Mock StorageService storageService;
    @Mock AvatarResolver avatarResolver;

    @InjectMocks PublicPostQueryService publicPostQueryService;

    private final Pageable pageable = PageRequest.of(0, 10);

    private Post publishedPost() {
        User author = new User(1, "Alice", "alice@test.com", new Role(Role.AUTHOR));
        author.setDescription("Bio");
        author.setProfilePicture("avatar.jpg");

        Category category = new Category("Fiction", "fiction");
        category.setId(1);

        Post post = new Post();
        post.setTitle("My Post");
        post.setContent("Content");
        post.setSlug("my-post");
        post.setStatus(PostStatus.PUBLISHED);
        post.setCoverImage("cover.jpg");
        post.setFocalX(new BigDecimal("0.25"));
        post.setFocalY(new BigDecimal("0.75"));
        post.setAuthor(author);
        post.setCategory(category);
        post.setCreatedAt(LocalDateTime.now());
        return post;
    }

    @Test
    void listPublishedPosts_withCategoryId_callsFindByCategoryIdAndStatus() {
        given(postRepository.findByCategoryIdAndStatus(1, PostStatus.PUBLISHED, pageable))
                .willReturn(new PageImpl<>(List.of(publishedPost()), pageable, 1));

        Page<PublicPostResponse> result = publicPostQueryService.listPublishedPosts(1, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(postRepository).findByCategoryIdAndStatus(1, PostStatus.PUBLISHED, pageable);
        verify(postRepository, never()).findByStatus(any(), any());
    }

    @Test
    void listPublishedPosts_nullCategoryId_callsFindByStatus() {
        given(postRepository.findByStatus(PostStatus.PUBLISHED, pageable))
                .willReturn(new PageImpl<>(List.of(publishedPost()), pageable, 1));

        Page<PublicPostResponse> result = publicPostQueryService.listPublishedPosts(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
        verify(postRepository, never()).findByCategoryIdAndStatus(any(), any(), any());
    }

    @Test
    void listPublishedPosts_overload_delegatesWithNullCategoryId() {
        given(postRepository.findByStatus(PostStatus.PUBLISHED, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        publicPostQueryService.listPublishedPosts(pageable);

        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
    }

    @Test
    void listPublishedPosts_mapsPostToPublicPostResponseFields() {
        given(postRepository.findByStatus(PostStatus.PUBLISHED, pageable))
                .willReturn(new PageImpl<>(List.of(publishedPost()), pageable, 1));
        given(avatarResolver.resolve("avatar.jpg", "Alice")).willReturn("https://avatar-url");
        given(storageService.buildUrl("cover.jpg")).willReturn("https://cover-url");

        Page<PublicPostResponse> result = publicPostQueryService.listPublishedPosts(pageable);

        PublicPostResponse response = result.getContent().get(0);
        assertThat(response.title()).isEqualTo("My Post");
        assertThat(response.slug()).isEqualTo("my-post");
        assertThat(response.authorId()).isEqualTo(1);
        assertThat(response.authorName()).isEqualTo("Alice");
        assertThat(response.authorDescription()).isEqualTo("Bio");
        assertThat(response.authorProfilePicture()).isEqualTo("https://avatar-url");
        assertThat(response.categoryName()).isEqualTo("Fiction");
        assertThat(response.categorySlug()).isEqualTo("fiction");
        assertThat(response.coverImage()).isEqualTo("https://cover-url");
        assertThat(response.focalX()).isEqualByComparingTo("0.25");
        assertThat(response.focalY()).isEqualByComparingTo("0.75");
        assertThat(response.featured()).isFalse();
    }

    @Test
    void listPublishedPosts_mapsFeaturedFlag() {
        Post post = publishedPost();
        post.setFeatured(true);
        given(postRepository.findByStatus(PostStatus.PUBLISHED, pageable))
                .willReturn(new PageImpl<>(List.of(post), pageable, 1));

        Page<PublicPostResponse> result = publicPostQueryService.listPublishedPosts(pageable);

        assertThat(result.getContent().get(0).featured()).isTrue();
    }

    @Test
    void listPublishedPosts_withCatalogSort_appliesWhitelistedSortIgnoringPageableSort() {
        Pageable incoming = PageRequest.of(2, 5, Sort.by(Sort.Order.desc("moderationNote")));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        given(postRepository.findByStatus(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        publicPostQueryService.listPublishedPosts(null, PostCatalogSort.FEATURED, incoming);

        verify(postRepository).findByStatus(any(), captor.capture());
        Pageable effective = captor.getValue();
        assertThat(effective.getPageNumber()).isEqualTo(2);
        assertThat(effective.getPageSize()).isEqualTo(5);
        assertThat(effective.getSort()).isEqualTo(PostCatalogSort.FEATURED.toSort());
    }

    @Test
    void getBySlug_publishedPost_returnsResponse() {
        given(postRepository.findBySlugAndStatus("my-post", PostStatus.PUBLISHED))
                .willReturn(Optional.of(publishedPost()));

        PublicPostResponse result = publicPostQueryService.getBySlug("my-post");

        assertThat(result.slug()).isEqualTo("my-post");
        assertThat(result.title()).isEqualTo("My Post");
    }

    @Test
    void getBySlug_nonExistentOrUnpublished_throwsResourceNotFoundException() {
        given(postRepository.findBySlugAndStatus("unknown", PostStatus.PUBLISHED))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> publicPostQueryService.getBySlug("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
