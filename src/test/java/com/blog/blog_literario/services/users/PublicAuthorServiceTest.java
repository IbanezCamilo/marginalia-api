package com.blog.blog_literario.services.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.dto.users.PublicAuthorResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.posts.PublicPostMapper;

@ExtendWith(MockitoExtension.class)
class PublicAuthorServiceTest {

    private static final String STORED_AVATAR = "https://avatar-url";
    private static final String INITIALS_AVATAR = "https://initials-avatar";

    @Mock UserRepository userRepository;
    @Mock PostRepository postRepository;
    @Mock AvatarResolver avatarResolver;
    @Mock PublicPostMapper publicPostMapper;
    @Mock AuthorVisibilityResolver authorVisibilityResolver;

    @InjectMocks PublicAuthorService publicAuthorService;

    private final Pageable pageable = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        lenient().when(avatarResolver.resolve("avatar.jpg", "Alice")).thenReturn(STORED_AVATAR);
        lenient().when(avatarResolver.resolve(null, "Alice")).thenReturn(INITIALS_AVATAR);
    }

    private User author() {
        User author = new User(1, "Alice", "alice@test.com", new Role(Role.AUTHOR));
        author.setDescription("Bio");
        author.setProfilePicture("avatar.jpg");
        return author;
    }

    /** Resolves author 1 with the given privacy choices and returns their public profile. */
    private PublicAuthorResponse profileWith(boolean bio, boolean photo) {
        given(userRepository.findById(1)).willReturn(Optional.of(author()));
        given(authorVisibilityResolver.forAuthor(1))
                .willReturn(new PublicProfileVisibility(bio, photo));

        return publicAuthorService.getAuthorById(1);
    }

    @Test
    void getAuthorById_everythingVisible_returnsPublicAuthorResponse() {
        PublicAuthorResponse result = profileWith(true, true);

        assertThat(result.id()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.description()).isEqualTo("Bio");
        assertThat(result.profilePicture()).isEqualTo(STORED_AVATAR);
    }

    @Test
    void getAuthorById_bioHidden_omitsBioAndKeepsPhoto() {
        PublicAuthorResponse result = profileWith(false, true);

        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.description()).isNull();
        assertThat(result.profilePicture()).isEqualTo(STORED_AVATAR);
    }

    @Test
    void getAuthorById_photoHidden_servesInitialsAvatarAndKeepsBio() {
        PublicAuthorResponse result = profileWith(true, false);

        assertThat(result.description()).isEqualTo("Bio");
        assertThat(result.profilePicture()).isEqualTo(INITIALS_AVATAR);
    }

    /**
     * The combined case: each redaction passing on its own does not prove they
     * compose, so the both-hidden corner is asserted explicitly. The name and the
     * avatar URL must both survive — the DTO shape never gains a null it didn't have.
     */
    @Test
    void getAuthorById_bioAndPhotoHidden_omitsBioAndStillServesANonNullInitialsAvatar() {
        PublicAuthorResponse result = profileWith(false, false);

        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.description()).isNull();
        assertThat(result.profilePicture()).isNotNull().isEqualTo(INITIALS_AVATAR);
    }

    @Test
    void getAuthorById_nonExistent_throwsResourceNotFoundException() {
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicAuthorService.getAuthorById(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPublishedPostsByAuthor_existingAuthor_returnsMappedPage() {
        User author = author();
        Category category = new Category("Fiction", "fiction");
        category.setId(1);

        Post post = new Post();
        post.setTitle("My Post");
        post.setContent("Content");
        post.setSlug("my-post");
        post.setStatus(PostStatus.PUBLISHED);
        post.setAuthor(author);
        post.setCategory(category);
        post.setCreatedAt(LocalDateTime.now());

        given(userRepository.findById(1)).willReturn(Optional.of(author));
        given(authorVisibilityResolver.forAuthor(1)).willReturn(PublicProfileVisibility.VISIBLE);
        given(postRepository.findByAuthorIdAndStatus(1, PostStatus.PUBLISHED, pageable))
                .willReturn(new PageImpl<>(List.of(post), pageable, 1));
        given(publicPostMapper.toResponse(eq(post), any(PublicProfileVisibility.class)))
                .willReturn(response("my-post"));

        Page<PublicPostResponse> result = publicAuthorService.getPublishedPostsByAuthor(1, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).slug()).isEqualTo("my-post");
    }

    /** One visibility lookup for the whole page — every post shares the same author. */
    @Test
    void getPublishedPostsByAuthor_multiplePosts_resolvesVisibilityOnce() {
        User author = author();
        Category category = new Category("Fiction", "fiction");
        category.setId(1);

        Post first = new Post();
        first.setSlug("first");
        first.setAuthor(author);
        first.setCategory(category);
        Post second = new Post();
        second.setSlug("second");
        second.setAuthor(author);
        second.setCategory(category);

        given(userRepository.findById(1)).willReturn(Optional.of(author));
        given(authorVisibilityResolver.forAuthor(1))
                .willReturn(new PublicProfileVisibility(false, false));
        given(postRepository.findByAuthorIdAndStatus(1, PostStatus.PUBLISHED, pageable))
                .willReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        given(publicPostMapper.toResponse(any(Post.class), any(PublicProfileVisibility.class)))
                .willReturn(response("first"), response("second"));

        publicAuthorService.getPublishedPostsByAuthor(1, pageable);

        verify(authorVisibilityResolver).forAuthor(1);
        verify(publicPostMapper).toResponse(first, new PublicProfileVisibility(false, false));
        verify(publicPostMapper).toResponse(second, new PublicProfileVisibility(false, false));
    }

    @Test
    void getPublishedPostsByAuthor_nonExistentAuthor_throwsResourceNotFoundException() {
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicAuthorService.getPublishedPostsByAuthor(99, pageable))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(postRepository, never()).findByAuthorIdAndStatus(any(), any(), any());
    }

    private PublicPostResponse response(String slug) {
        return new PublicPostResponse("My Post", "Content", slug, 1, "Alice", null, INITIALS_AVATAR,
                "Fiction", "fiction", null, null, null, LocalDateTime.now(), false, 1);
    }
}
