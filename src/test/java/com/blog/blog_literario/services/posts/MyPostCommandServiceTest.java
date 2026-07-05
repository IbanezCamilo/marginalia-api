package com.blog.blog_literario.services.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Optional;

import com.blog.blog_literario.dto.posts.CreatePostRequest;
import com.blog.blog_literario.dto.posts.MyPostResponse;
import com.blog.blog_literario.dto.posts.UpdatePostRequest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    void update_draftPost_updatesTitleContentAndStatus() {
        Post post = new Post("Old Title", "Old Content", PostStatus.DRAFT, "old-title", author, category);
        UpdatePostRequest request = new UpdatePostRequest("New Title Here", TIPTAP_CONTENT, null, "DRAFT");
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));
        given(postRepository.existsBySlugAndIdNot(anyString(), any())).willReturn(false);

        MyPostResponse result = myService.update(1, 1, request);

        assertThat(result.title()).isEqualTo("New Title Here");
    }

    @Test
    void update_publishedPost_throwsIllegalState_andDoesNotMutate() {
        Post post = new Post("Published Post", "Content", PostStatus.PUBLISHED, "published-post", author, category);
        UpdatePostRequest request = new UpdatePostRequest("Published Post", "Sneaky edit", null, "PUBLISHED");
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.update(1, 1, request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(post.getContent()).isEqualTo("Content");
    }

    @Test
    void update_archivedPost_throwsIllegalState_andDoesNotMutate() {
        Post post = new Post("Archived Post", "Content", PostStatus.ARCHIVED, "archived-post", author, category);
        UpdatePostRequest request = new UpdatePostRequest("Archived Post", "Sneaky edit", null, "ARCHIVED");
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.update(1, 1, request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(post.getContent()).isEqualTo("Content");
    }

    // --- Author edit transition matrix ---------------------------------------
    //              → DRAFT    → PUBLISHED
    //   DRAFT      allowed    allowed
    //   REJECTED   allowed    blocked
    //   PUBLISHED  allowed    blocked
    //   ARCHIVED   blocked    blocked

    @ParameterizedTest(name = "update {0} → {1} is allowed")
    @CsvSource({
            "DRAFT,     DRAFT",
            "DRAFT,     PUBLISHED",
            "REJECTED,  DRAFT",
            "PUBLISHED, DRAFT",
    })
    void update_allowedTransition_savesAndSetsTargetStatus(PostStatus current, PostStatus target) {
        Post post = new Post("Old Title", "Old Content", current, "old-title", author, category);
        UpdatePostRequest request = new UpdatePostRequest("New Title Here", TIPTAP_CONTENT, 1, target.name());
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));
        given(categoryRepository.findById(1)).willReturn(Optional.of(category));

        MyPostResponse result = myService.update(1, 1, request);

        assertThat(result.status()).isEqualTo(target.name());
        assertThat(result.title()).isEqualTo("New Title Here");
    }

    @ParameterizedTest(name = "update {0} → {1} is blocked")
    @CsvSource({
            "REJECTED,  PUBLISHED",
            "PUBLISHED, PUBLISHED",
            "ARCHIVED,  DRAFT",
            "ARCHIVED,  PUBLISHED",
    })
    void update_blockedTransition_throwsIllegalState_andDoesNotMutate(PostStatus current, PostStatus target) {
        Post post = new Post("Original Title", "Original Content", current, "original-title", author, category);
        UpdatePostRequest request = new UpdatePostRequest("Sneaky Title", "Sneaky edit", 1, target.name());
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.update(1, 1, request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(post.getContent()).isEqualTo("Original Content");
        assertThat(post.getStatus()).isEqualTo(current);
    }

    @Test
    void update_rejectedPermanentlyBlocked_toDraft_throwsIllegalState_andDoesNotMutate() {
        Post post = new Post("Rejected Post", "Content", PostStatus.REJECTED, "rejected-post", author, category);
        post.setRejectionCount(3);
        UpdatePostRequest request = new UpdatePostRequest("Rejected Post", "Sneaky edit", null, "DRAFT");
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.update(1, 1, request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(post.getContent()).isEqualTo("Content");
    }

    @Test
    void create_draftWithOnlyContent_succeeds() {
        CreatePostRequest request = new CreatePostRequest(null, TIPTAP_CONTENT, null, "DRAFT");
        given(userRepository.findById(1)).willReturn(Optional.of(author));

        MyPostResponse result = myService.create(1, request);

        assertThat(result.title()).isNull();
        assertThat(result.slug()).isNull();
        assertThat(result.categoryId()).isNull();
        verify(postRepository, never()).existsBySlug(any());
        verify(categoryRepository, never()).findById(any());
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void create_twoDraftsWithNoTitle_bothSucceed_withoutSlugCollisionCheck() {
        CreatePostRequest request = new CreatePostRequest(null, TIPTAP_CONTENT, null, "DRAFT");
        given(userRepository.findById(1)).willReturn(Optional.of(author));

        myService.create(1, request);
        myService.create(1, request);

        verify(postRepository, never()).existsBySlug(any());
        verify(postRepository, org.mockito.Mockito.times(2)).save(any(Post.class));
    }

    @Test
    void create_publishWithoutTitleContentCategory_throwsIllegalArgumentException() {
        CreatePostRequest request = new CreatePostRequest(null, null, null, "PUBLISHED");
        given(userRepository.findById(1)).willReturn(Optional.of(author));

        assertThatThrownBy(() -> myService.create(1, request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void update_clearingTitle_clearsSlugToo() {
        Post post = new Post("Old Title", "Old Content", PostStatus.DRAFT, "old-title", author, category);
        UpdatePostRequest request = new UpdatePostRequest(null, TIPTAP_CONTENT, null, "DRAFT");
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        MyPostResponse result = myService.update(1, 1, request);

        assertThat(result.title()).isNull();
        assertThat(result.slug()).isNull();
        verify(postRepository, never()).existsBySlugAndIdNot(any(), any());
    }

    @Test
    void update_publishWithoutRequiredFields_throwsIllegalArgumentException_andDoesNotMutate() {
        Post post = new Post(null, null, PostStatus.DRAFT, null, author, null);
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, "PUBLISHED");
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.update(1, 1, request))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    void updateStatus_publishWithoutTitleOrCategory_throwsIllegalArgumentException() {
        Post post = new Post(null, "Content", PostStatus.DRAFT, null, author, null);
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> myService.updateStatus(1, 1, "PUBLISHED"))
                .isInstanceOf(IllegalArgumentException.class);

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

    // --- Cover-image focal point ---------------------------------------------

    @Test
    void create_withFocalPoint_persistsAndReturnsIt() {
        CreatePostRequest request = new CreatePostRequest(
                "Focal Post", TIPTAP_CONTENT, 1, "DRAFT",
                new BigDecimal("0.25"), new BigDecimal("0.75"));
        given(userRepository.findById(1)).willReturn(Optional.of(author));
        given(categoryRepository.findById(1)).willReturn(Optional.of(category));
        given(postRepository.existsBySlug("focal-post")).willReturn(false);

        MyPostResponse result = myService.create(1, request);

        assertThat(result.focalX()).isEqualByComparingTo("0.25");
        assertThat(result.focalY()).isEqualByComparingTo("0.75");

        org.mockito.ArgumentCaptor<Post> saved = org.mockito.ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(saved.capture());
        assertThat(saved.getValue().getFocalX()).isEqualByComparingTo("0.25");
        assertThat(saved.getValue().getFocalY()).isEqualByComparingTo("0.75");
    }

    @Test
    void create_withoutFocalPoint_defaultsToCenter() {
        CreatePostRequest request = new CreatePostRequest(null, TIPTAP_CONTENT, null, "DRAFT");
        given(userRepository.findById(1)).willReturn(Optional.of(author));

        MyPostResponse result = myService.create(1, request);

        assertThat(result.focalX()).isEqualByComparingTo("0.5");
        assertThat(result.focalY()).isEqualByComparingTo("0.5");
    }

    @Test
    void update_withFocalPoint_updatesStoredValue() {
        Post post = new Post("Old Title", "Old Content", PostStatus.DRAFT, "old-title", author, category);
        UpdatePostRequest request = new UpdatePostRequest(
                "New Title Here", TIPTAP_CONTENT, 1, "DRAFT",
                new BigDecimal("0.1"), new BigDecimal("0.9"));
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));
        given(categoryRepository.findById(1)).willReturn(Optional.of(category));
        given(postRepository.existsBySlugAndIdNot(anyString(), any())).willReturn(false);

        MyPostResponse result = myService.update(1, 1, request);

        assertThat(result.focalX()).isEqualByComparingTo("0.1");
        assertThat(result.focalY()).isEqualByComparingTo("0.9");
        assertThat(post.getFocalX()).isEqualByComparingTo("0.1");
        assertThat(post.getFocalY()).isEqualByComparingTo("0.9");
    }

    @Test
    void update_withoutFocalPoint_keepsExistingValue() {
        Post post = new Post("Old Title", "Old Content", PostStatus.DRAFT, "old-title", author, category);
        post.setFocalX(new BigDecimal("0.2"));
        post.setFocalY(new BigDecimal("0.8"));
        UpdatePostRequest request = new UpdatePostRequest("New Title Here", TIPTAP_CONTENT, null, "DRAFT");
        given(postRepository.findByIdAndAuthorId(1, 1)).willReturn(Optional.of(post));

        MyPostResponse result = myService.update(1, 1, request);

        assertThat(result.focalX()).isEqualByComparingTo("0.2");
        assertThat(result.focalY()).isEqualByComparingTo("0.8");
    }
}
