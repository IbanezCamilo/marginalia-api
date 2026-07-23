package com.blog.blog_literario.services.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;
import com.blog.blog_literario.services.users.PublicProfileVisibility;

@ExtendWith(MockitoExtension.class)
class PublicPostMapperTest {

    private static final String STORED_AVATAR = "https://stored-avatar";
    private static final String INITIALS_AVATAR = "https://initials-avatar";

    @Mock StorageService storageService;
    @Mock AvatarResolver avatarResolver;

    @InjectMocks PublicPostMapper publicPostMapper;

    @BeforeEach
    void setUp() {
        // The resolver already falls back to the generated initials avatar for a null
        // filename; hiding the photo simply routes into that existing path.
        lenient().when(avatarResolver.resolve("avatar.jpg", "Alice")).thenReturn(STORED_AVATAR);
        lenient().when(avatarResolver.resolve(null, "Alice")).thenReturn(INITIALS_AVATAR);
    }

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
        post.setAuthor(author);
        post.setCategory(category);
        post.setCreatedAt(LocalDateTime.now());
        return post;
    }

    private PublicPostResponse map(boolean bio, boolean photo) {
        return publicPostMapper.toResponse(publishedPost(), new PublicProfileVisibility(bio, photo));
    }

    @Test
    void toResponse_everythingVisible_serializesStoredBioAndPhoto() {
        given(storageService.buildUrl("cover.jpg")).willReturn("https://cover-url");

        PublicPostResponse response = map(true, true);

        assertThat(response.authorName()).isEqualTo("Alice");
        assertThat(response.authorDescription()).isEqualTo("Bio");
        assertThat(response.authorProfilePicture()).isEqualTo(STORED_AVATAR);
        assertThat(response.coverImage()).isEqualTo("https://cover-url");
    }

    @Test
    void toResponse_bioHidden_dropsBioAndKeepsPhoto() {
        PublicPostResponse response = map(false, true);

        assertThat(response.authorDescription()).isNull();
        assertThat(response.authorProfilePicture()).isEqualTo(STORED_AVATAR);
    }

    @Test
    void toResponse_photoHidden_servesInitialsAvatarAndKeepsBio() {
        PublicPostResponse response = map(true, false);

        assertThat(response.authorDescription()).isEqualTo("Bio");
        assertThat(response.authorProfilePicture()).isEqualTo(INITIALS_AVATAR);
    }

    /**
     * The combined case: two redactions that each pass in isolation can still
     * interfere with one another, so the both-hidden corner is asserted explicitly.
     */
    @Test
    void toResponse_bioAndPhotoHidden_dropsBioAndStillServesANonNullInitialsAvatar() {
        PublicPostResponse response = map(false, false);

        assertThat(response.authorDescription()).isNull();
        assertThat(response.authorProfilePicture()).isNotNull().isEqualTo(INITIALS_AVATAR);
    }

    @Test
    void toResponse_authorNameIsNeverRedacted() {
        assertThat(map(true, true).authorName()).isEqualTo("Alice");
        assertThat(map(false, true).authorName()).isEqualTo("Alice");
        assertThat(map(true, false).authorName()).isEqualTo("Alice");
        assertThat(map(false, false).authorName()).isEqualTo("Alice");
    }
}
