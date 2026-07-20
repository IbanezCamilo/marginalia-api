package com.blog.blog_literario.services.moderator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import com.blog.blog_literario.dto.moderator.ModeratorFeaturedUpdateRequest;
import com.blog.blog_literario.dto.moderator.ModeratorPostResponse;
import com.blog.blog_literario.dto.moderator.ModeratorStatusUpdateRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.admin.AdminActionLogService;
import com.blog.blog_literario.services.images.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModeratorPostServiceTest {

    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;
    @Mock StorageService storageService;
    @Mock AdminActionLogService adminActionLogService;
    @Mock PostModerationEventPublisher moderationEventPublisher;

    @InjectMocks ModeratorPostService moderatorService;

    private User author;
    private User moderator;
    private Category category;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        author = new User(2, "Alice", "alice@test.com", new Role("AUTHOR"));
        moderator = new User(1, "Mod", "mod@test.com", new Role("MODERATOR"));
        category = new Category();
        category.setId(1);
        category.setName("Fiction");
        category.setSlug("fiction");
        pageable = PageRequest.of(0, 10);
        given(storageService.buildUrl(any())).willReturn(null);
    }

    private Post newPost(PostStatus status) {
        return new Post("Title", "Content", status, "title-slug", author, category);
    }

    @Test
    void listAll_noStatusFilter_usesFindAll() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(post)));

        moderatorService.listAll(null, pageable);

        verify(postRepository).findAll(pageable);
        verify(postRepository, never()).findByStatus(any(), any());
    }

    @Test
    void listAll_withStatusFilter_usesFindByStatus() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findByStatus(PostStatus.DRAFT, pageable)).willReturn(new PageImpl<>(List.of(post)));

        moderatorService.listAll(PostStatus.DRAFT, pageable);

        verify(postRepository).findByStatus(PostStatus.DRAFT, pageable);
        verify(postRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listAll_draftWithoutCategory_mapsNullCategoryName() {
        Post post = new Post("Title", "Content", PostStatus.DRAFT, "title-slug", author, null);
        given(postRepository.findByStatus(PostStatus.DRAFT, pageable)).willReturn(new PageImpl<>(List.of(post)));

        var result = moderatorService.listAll(PostStatus.DRAFT, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).categoryName()).isNull();
    }

    @Test
    void updateStatus_draftToPublished_succeeds() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("PUBLISHED", null));

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_draftToRejected_withNote_incrementsRejection() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("REJECTED", "Needs fixes"));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(post.getRejectionCount()).isEqualTo(1);
        assertThat(post.getModerationNote()).isEqualTo("Needs fixes");
    }

    @Test
    void updateStatus_publishedToRejected_succeeds() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("REJECTED", "Needs fixes"));

        assertThat(result.status()).isEqualTo("REJECTED");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_publishedToArchived_succeeds() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("ARCHIVED", null));

        assertThat(result.status()).isEqualTo("ARCHIVED");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_rejectedToPublished_succeeds() {
        Post post = newPost(PostStatus.REJECTED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("PUBLISHED", null));

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_rejectedToDraft_succeeds() {
        Post post = newPost(PostStatus.REJECTED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("DRAFT", null));

        assertThat(result.status()).isEqualTo("DRAFT");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_archivedToAnything_throwsIllegalState() {
        Post post = newPost(PostStatus.ARCHIVED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("DRAFT", null)))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateStatus_draftToArchived_throwsIllegalState() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("ARCHIVED", null)))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateStatus_publishedToDraft_throwsIllegalState() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("DRAFT", null)))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateStatus_toRejected_withoutNote_throwsIllegalState() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("REJECTED", "  ")))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateStatus_toRejected_onPermanentlyBlockedPost_throwsIllegalState() {
        Post post = newPost(PostStatus.DRAFT);
        post.setRejectionCount(3);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("REJECTED", "Needs fixes")))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateStatus_toRejected_thirdRejection_autoArchives() {
        Post post = newPost(PostStatus.DRAFT);
        post.setRejectionCount(2);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("REJECTED", "Last warning"));

        assertThat(result.status()).isEqualTo("ARCHIVED");
        assertThat(post.getRejectionCount()).isEqualTo(3);
    }

    @Test
    void updateStatus_invalidStatus_throwsIllegalArgument() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("BOGUS", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_postNotFound_throwsResourceNotFound() {
        given(postRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> moderatorService.updateStatus(1, 99, new ModeratorStatusUpdateRequest("PUBLISHED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_moderatorNotFound_throwsResourceNotFound() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> moderatorService.updateStatus(99, 1, new ModeratorStatusUpdateRequest("PUBLISHED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_draftToPublished_notifiesWithPreviousStatus() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("PUBLISHED", null));

        verify(moderationEventPublisher).publishStatusChange(post, 1, PostStatus.DRAFT);
    }

    @Test
    void updateStatus_rejection_notifiesWithPreviousStatus() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("REJECTED", "Corrige el título"));

        verify(moderationEventPublisher).publishStatusChange(post, 1, PostStatus.PUBLISHED);
    }

    @Test
    void updateStatus_invalidTransition_doesNotNotify() {
        Post post = newPost(PostStatus.ARCHIVED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> moderatorService.updateStatus(
                1, 1, new ModeratorStatusUpdateRequest("PUBLISHED", null)))
                .isInstanceOf(IllegalStateException.class);

        verify(moderationEventPublisher, never()).publishStatusChange(any(), any(), any());
    }

    @Test
    void updateFeatured_publishedPost_setsFlagSavesAndLogs() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateFeatured(1, 1, new ModeratorFeaturedUpdateRequest(true));

        assertThat(result.featured()).isTrue();
        assertThat(post.isFeatured()).isTrue();
        verify(postRepository).save(post);
        verify(adminActionLogService).record(
                eq(1), eq("mod@test.com"), eq("POST_FEATURED_CHANGE"), eq("POST"), eq(1), any());
    }

    @Test
    void updateFeatured_nonPublishedPost_throwsIllegalState() {
        for (PostStatus status : List.of(PostStatus.DRAFT, PostStatus.REJECTED, PostStatus.ARCHIVED)) {
            Post post = newPost(status);
            given(postRepository.findById(1)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> moderatorService.updateFeatured(1, 1, new ModeratorFeaturedUpdateRequest(true)))
                    .isInstanceOf(IllegalStateException.class);
        }

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateFeatured_unfeatureNonPublishedPost_succeeds() {
        Post post = newPost(PostStatus.ARCHIVED);
        post.setFeatured(true);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        ModeratorPostResponse result = moderatorService.updateFeatured(1, 1, new ModeratorFeaturedUpdateRequest(false));

        assertThat(result.featured()).isFalse();
        assertThat(post.isFeatured()).isFalse();
        verify(postRepository).save(post);
    }

    @Test
    void updateFeatured_sameValue_isNoOpWithoutSaveOrLog() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        ModeratorPostResponse result = moderatorService.updateFeatured(1, 1, new ModeratorFeaturedUpdateRequest(false));

        assertThat(result.featured()).isFalse();
        verify(postRepository, never()).save(any());
        verify(adminActionLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateFeatured_doesNotTouchModerationMetadata() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        moderatorService.updateFeatured(1, 1, new ModeratorFeaturedUpdateRequest(true));

        assertThat(post.getModeratedBy()).isNull();
        assertThat(post.getModeratedAt()).isNull();
        assertThat(post.getModerationNote()).isNull();
    }

    @Test
    void updateFeatured_postNotFound_throwsResourceNotFound() {
        given(postRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> moderatorService.updateFeatured(1, 99, new ModeratorFeaturedUpdateRequest(true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
