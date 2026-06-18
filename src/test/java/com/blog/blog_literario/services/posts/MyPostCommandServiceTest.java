package com.blog.blog_literario.services.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import com.blog.blog_literario.dto.posts.CreatePostRequest;
import com.blog.blog_literario.dto.posts.MyPostResponse;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyPostCommandServiceTest {

    private static final String TIPTAP_CONTENT =
            "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Content\"}]}]}";

    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock StorageService storageService;

    @InjectMocks MyPostCommandService myService;

    private User author;
    private Category category;

    @BeforeEach
    void setUp() {
        author = new User(1, "Alice", "alice@test.com", new Role("AUTHOR"));
        category = new Category();
        category.setId(1);
        category.setName("Fiction");
        category.setSlug("fiction");
        given(storageService.buildUrl(any())).willReturn(null);
    }

    @Test
    void updateStatus_draftToPublished_succeeds() {
        Post post = new Post("Draft Post", "Content", PostStatus.DRAFT, "draft-post", author, category);
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        MyPostResponse result = myService.updateStatus(1, 1, "PUBLISHED");

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_archivedToAny_throwsIllegalState() {
        Post post = new Post("Archived Post", "Content", PostStatus.ARCHIVED, "archived-post", author, category);
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.updateStatus(1, 1, "DRAFT"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateStatus_draftToArchived_throwsIllegalState() {
        Post post = new Post("Draft Post", "Content", PostStatus.DRAFT, "draft-post", author, category);
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.updateStatus(1, 1, "ARCHIVED"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateStatus_postNotOwnedByUser_throwsException() {
        given(postRepository.findByIdAndAuthorId(99, 1)).willReturn(Optional.empty());

        assertThatThrownBy(() -> myService.updateStatus(1, 99, "PUBLISHED"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateStatus_rejectedToDraft_belowThreshold_succeeds() {
        Post post = new Post("Rejected Post", "Content", PostStatus.REJECTED, "rejected-post", author, category);
        post.setRejectionCount(2);
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        MyPostResponse result = myService.updateStatus(1, 1, "DRAFT");

        assertThat(result.status()).isEqualTo("DRAFT");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_rejectedToDraft_permanentlyBlocked_throwsIllegalState() {
        Post post = new Post("Rejected Post", "Content", PostStatus.REJECTED, "rejected-post", author, category);
        post.setRejectionCount(3);
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.updateStatus(1, 1, "DRAFT"))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateStatus_responseIncludesModerationFields() {
        Post post = new Post("Rejected Post", "Content", PostStatus.REJECTED, "rejected-post", author, category);
        post.setModerationNote("Needs more detail");
        post.setRejectionCount(2);
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        MyPostResponse result = myService.updateStatus(1, 1, "DRAFT");

        assertThat(result.moderationNote()).isEqualTo("Needs more detail");
        assertThat(result.rejectionCount()).isEqualTo(2);
        assertThat(result.canBeResubmitted()).isTrue();
        assertThat(result.isLastAttempt()).isTrue();
    }

    @Test
    void create_uniqueSlug_savesPostAndReturnsResponse() {
        CreatePostRequest request = new CreatePostRequest("Test Title Post", TIPTAP_CONTENT, 1, "DRAFT");
        given(userRepository.findById(1)).willReturn(Optional.of(author));
        given(categoryRepository.findById(1)).willReturn(Optional.of(category));
        given(postRepository.existsBySlug("test-title-post")).willReturn(false);

        MyPostResponse result = myService.create(1, request);

        assertThat(result.title()).isEqualTo("Test Title Post");
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void create_duplicateSlug_throwsExceptionBeforeSave() {
        CreatePostRequest request = new CreatePostRequest("Test Title Post", "Content", 1, "DRAFT");
        given(userRepository.findById(1)).willReturn(Optional.of(author));
        given(categoryRepository.findById(1)).willReturn(Optional.of(category));
        given(postRepository.existsBySlug(anyString())).willReturn(true);

        assertThatThrownBy(() -> myService.create(1, request))
                .isInstanceOf(RuntimeException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void delete_existingPost_deletesImageThenPost() {
        Post post = new Post("Post", "Content", PostStatus.DRAFT, "post-slug", author, category);
        post.setCoverImage("cover.jpg");
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        myService.delete(1, 1);

        InOrder order = inOrder(storageService, postRepository);
        order.verify(storageService).delete("cover.jpg");
        order.verify(postRepository).delete(post);
    }
}
