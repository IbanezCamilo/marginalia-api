package com.blog.blog_literario.services.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.blog.blog_literario.dto.posts.PostCatalogFilter;
import com.blog.blog_literario.dto.posts.PostCatalogSort;
import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.dto.posts.ReadingTimeBucket;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostCatalogSpecifications;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;
import com.blog.blog_literario.services.users.AuthorVisibilityResolver;
import com.blog.blog_literario.services.users.PublicProfileVisibility;

@ExtendWith(MockitoExtension.class)
class PublicPostQueryServiceTest {

    @Mock PostRepository postRepository;
    @Mock StorageService storageService;
    @Mock AvatarResolver avatarResolver;
    @Mock AuthorVisibilityResolver authorVisibilityResolver;

    // The mapper is exercised for real here so the field-mapping assertions below stay
    // meaningful; only its collaborators are stubbed.
    private PublicPostQueryService publicPostQueryService;

    private final Pageable pageable = PageRequest.of(0, 10);
    private static final PostCatalogFilter NO_FILTER = PostCatalogFilter.of(null, null, null, null, null);

    @BeforeEach
    void setUp() {
        publicPostQueryService = new PublicPostQueryService(
                postRepository,
                new PublicPostMapper(storageService, avatarResolver),
                authorVisibilityResolver);

        lenient().when(avatarResolver.resolve("avatar.jpg", "Alice")).thenReturn("https://avatar-url");
        lenient().when(avatarResolver.resolve(null, "Alice")).thenReturn("https://initials-alice");
        lenient().when(avatarResolver.resolve("bob.jpg", "Bob")).thenReturn("https://avatar-bob");
        lenient().when(avatarResolver.resolve(null, "Bob")).thenReturn("https://initials-bob");
    }

    private Post publishedPost() {
        return publishedPostBy(new User(1, "Alice", "alice@test.com", new Role(Role.AUTHOR)),
                "avatar.jpg", "my-post");
    }

    private Post publishedPostBy(User author, String profilePicture, String slug) {
        author.setDescription("Bio de " + author.getName());
        author.setProfilePicture(profilePicture);

        Category category = new Category("Fiction", "fiction");
        category.setId(1);

        Post post = new Post();
        post.setTitle("My Post");
        post.setContent("Content");
        post.setSlug(slug);
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
    void listPublishedPosts_delegatesToRepositoryFindAllWithSpecification() {
        given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(publishedPost()), pageable, 1));

        Page<PublicPostResponse> result = publicPostQueryService.listPublishedPosts(
                NO_FILTER, PostCatalogSort.FEATURED, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(postRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listPublishedPosts_mapsPostToPublicPostResponseFields() {
        given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(publishedPost()), pageable, 1));
        given(authorVisibilityResolver.forAuthors(Set.of(1)))
                .willReturn(Map.of(1, PublicProfileVisibility.VISIBLE));
        given(storageService.buildUrl("cover.jpg")).willReturn("https://cover-url");

        Page<PublicPostResponse> result = publicPostQueryService.listPublishedPosts(
                NO_FILTER, PostCatalogSort.FEATURED, pageable);

        PublicPostResponse response = result.getContent().get(0);
        assertThat(response.title()).isEqualTo("My Post");
        assertThat(response.slug()).isEqualTo("my-post");
        assertThat(response.authorId()).isEqualTo(1);
        assertThat(response.authorName()).isEqualTo("Alice");
        assertThat(response.authorDescription()).isEqualTo("Bio de Alice");
        assertThat(response.authorProfilePicture()).isEqualTo("https://avatar-url");
        assertThat(response.categoryName()).isEqualTo("Fiction");
        assertThat(response.categorySlug()).isEqualTo("fiction");
        assertThat(response.coverImage()).isEqualTo("https://cover-url");
        assertThat(response.focalX()).isEqualByComparingTo("0.25");
        assertThat(response.focalY()).isEqualByComparingTo("0.75");
        assertThat(response.featured()).isFalse();
    }

    /**
     * Two authors on one page with opposite privacy settings: the redaction must be
     * per author, not per page.
     */
    @Test
    void listPublishedPosts_appliesEachAuthorsOwnVisibility() {
        Post alicePost = publishedPost();
        Post bobPost = publishedPostBy(
                new User(2, "Bob", "bob@test.com", new Role(Role.AUTHOR)), "bob.jpg", "bob-post");

        given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(alicePost, bobPost), pageable, 2));
        given(authorVisibilityResolver.forAuthors(Set.of(1, 2))).willReturn(Map.of(
                1, PublicProfileVisibility.VISIBLE,
                2, new PublicProfileVisibility(false, false)));

        List<PublicPostResponse> posts = publicPostQueryService.listPublishedPosts(
                NO_FILTER, PostCatalogSort.FEATURED, pageable).getContent();

        assertThat(posts.get(0).authorDescription()).isEqualTo("Bio de Alice");
        assertThat(posts.get(0).authorProfilePicture()).isEqualTo("https://avatar-url");

        assertThat(posts.get(1).authorName()).isEqualTo("Bob");
        assertThat(posts.get(1).authorDescription()).isNull();
        assertThat(posts.get(1).authorProfilePicture()).isEqualTo("https://initials-bob");
    }

    /** One batched lookup for the whole page, never one per post. */
    @Test
    void listPublishedPosts_resolvesVisibilityOncePerDistinctAuthor() {
        Post first = publishedPost();
        Post second = publishedPostBy(first.getAuthor(), "avatar.jpg", "another-post");

        given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(first, second), pageable, 2));

        publicPostQueryService.listPublishedPosts(NO_FILTER, PostCatalogSort.FEATURED, pageable);

        verify(authorVisibilityResolver).forAuthors(Set.of(1));
    }

    @Test
    void listPublishedPosts_mapsFeaturedFlag() {
        Post post = publishedPost();
        post.setFeatured(true);
        given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post), pageable, 1));

        Page<PublicPostResponse> result = publicPostQueryService.listPublishedPosts(
                NO_FILTER, PostCatalogSort.FEATURED, pageable);

        assertThat(result.getContent().get(0).featured()).isTrue();
    }

    @Test
    void listPublishedPosts_withCatalogSort_appliesWhitelistedSortIgnoringPageableSort() {
        Pageable incoming = PageRequest.of(2, 5, Sort.by(Sort.Order.desc("moderationNote")));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        given(postRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        publicPostQueryService.listPublishedPosts(NO_FILTER, PostCatalogSort.FEATURED, incoming);

        verify(postRepository).findAll(any(Specification.class), captor.capture());
        Pageable effective = captor.getValue();
        assertThat(effective.getPageNumber()).isEqualTo(2);
        assertThat(effective.getPageSize()).isEqualTo(5);
        assertThat(effective.getSort()).isEqualTo(PostCatalogSort.FEATURED.toSort());
    }

    @Test
    void listPublishedPosts_withFilter_composesOneSpecificationPerActiveFacet() {
        PostCatalogFilter filter = new PostCatalogFilter(
                "ficcion", null, 7, ReadingTimeBucket.SHORT, "borges");

        try (MockedStatic<PostCatalogSpecifications> specs =
                     mockStatic(PostCatalogSpecifications.class)) {
            given(postRepository.findAll(ArgumentMatchers.<Specification<Post>>any(), any(Pageable.class)))
                    .willReturn(Page.empty());

            publicPostQueryService.listPublishedPosts(filter, PostCatalogSort.FEATURED, pageable);

            specs.verify(PostCatalogSpecifications::isPublished);
            specs.verify(() -> PostCatalogSpecifications.hasCategorySlug("ficcion"));
            specs.verify(() -> PostCatalogSpecifications.hasCategory(null));
            specs.verify(() -> PostCatalogSpecifications.hasAuthor(7));
            specs.verify(() -> PostCatalogSpecifications.readingTimeIn(ReadingTimeBucket.SHORT));
            specs.verify(() -> PostCatalogSpecifications.matchesQuery("borges"));
        }
    }

    @Test
    void getBySlug_publishedPost_returnsResponse() {
        given(postRepository.findBySlugAndStatus("my-post", PostStatus.PUBLISHED))
                .willReturn(Optional.of(publishedPost()));
        given(authorVisibilityResolver.forAuthor(1)).willReturn(PublicProfileVisibility.VISIBLE);

        PublicPostResponse result = publicPostQueryService.getBySlug("my-post");

        assertThat(result.slug()).isEqualTo("my-post");
        assertThat(result.title()).isEqualTo("My Post");
        assertThat(result.authorDescription()).isEqualTo("Bio de Alice");
    }

    @Test
    void getBySlug_authorHidesBioAndPhoto_redactsBoth() {
        given(postRepository.findBySlugAndStatus("my-post", PostStatus.PUBLISHED))
                .willReturn(Optional.of(publishedPost()));
        given(authorVisibilityResolver.forAuthor(1))
                .willReturn(new PublicProfileVisibility(false, false));

        PublicPostResponse result = publicPostQueryService.getBySlug("my-post");

        assertThat(result.authorName()).isEqualTo("Alice");
        assertThat(result.authorDescription()).isNull();
        assertThat(result.authorProfilePicture()).isEqualTo("https://initials-alice");
    }

    @Test
    void getBySlug_nonExistentOrUnpublished_throwsResourceNotFoundException() {
        given(postRepository.findBySlugAndStatus("unknown", PostStatus.PUBLISHED))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> publicPostQueryService.getBySlug("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
