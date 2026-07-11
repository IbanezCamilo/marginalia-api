package com.blog.blog_literario.services.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import com.blog.blog_literario.dto.admin.AdminPostResponse;
import com.blog.blog_literario.dto.admin.AdminStatusUpdateRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPostModerationServiceTest {

    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;
    @Mock StorageService storageService;
    @Mock AdminActionLogService adminActionLogService;

    @InjectMocks AdminPostModerationService adminService;

    private User author;
    private User admin;
    private Category category;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        author = new User(2, "Alice", "alice@test.com", new Role("AUTHOR"));
        admin = new User(1, "Admin", "admin@test.com", new Role("ADMIN"));
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
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(post)));

        adminService.listAll(null, pageable);

        verify(postRepository).findAll(pageable);
        verify(postRepository, never()).findByStatus(any(), any());
    }

    @Test
    void listAll_withStatusFilter_usesFindByStatus() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findByStatus(PostStatus.PUBLISHED, pageable)).willReturn(new PageImpl<>(List.of(post)));

        adminService.listAll(PostStatus.PUBLISHED, pageable);

        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
        verify(postRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listAll_draftWithoutCategory_mapsNullCategoryName() {
        Post post = new Post("Title", "Content", PostStatus.DRAFT, "title-slug", author, null);
        given(postRepository.findByStatus(PostStatus.DRAFT, pageable)).willReturn(new PageImpl<>(List.of(post)));

        var result = adminService.listAll(PostStatus.DRAFT, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).categoryName()).isNull();
    }

    @Test
    void updateStatus_toPublished_recordsModerationAndSaves() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(admin));

        AdminPostResponse result = adminService.updateStatus(1, 1, new AdminStatusUpdateRequest("PUBLISHED", "Looks good"));

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(post.getModeratedBy()).isEqualTo(admin);
        assertThat(post.getModerationNote()).isEqualTo("Looks good");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_toRejected_withNote_incrementsRejectionAndSetsRejected() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(admin));

        AdminPostResponse result = adminService.updateStatus(1, 1, new AdminStatusUpdateRequest("REJECTED", "Needs fixes"));

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(post.getRejectionCount()).isEqualTo(1);
        assertThat(post.getModerationNote()).isEqualTo("Needs fixes");
        verify(postRepository).save(post);
    }

    @Test
    void updateStatus_toRejected_thirdRejection_autoArchives() {
        Post post = newPost(PostStatus.PUBLISHED);
        post.setRejectionCount(2);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(admin));

        AdminPostResponse result = adminService.updateStatus(1, 1, new AdminStatusUpdateRequest("REJECTED", "Last warning"));

        assertThat(result.status()).isEqualTo("ARCHIVED");
        assertThat(post.getRejectionCount()).isEqualTo(3);
    }

    @Test
    void updateStatus_toRejected_withoutNote_throwsIllegalState_andDoesNotSave() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> adminService.updateStatus(1, 1, new AdminStatusUpdateRequest("REJECTED", "  ")))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateStatus_invalidStatus_throwsIllegalArgument() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> adminService.updateStatus(1, 1, new AdminStatusUpdateRequest("BOGUS", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_postNotFound_throwsResourceNotFound() {
        given(postRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateStatus(1, 99, new AdminStatusUpdateRequest("PUBLISHED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_adminNotFound_throwsResourceNotFound() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateStatus(99, 1, new AdminStatusUpdateRequest("PUBLISHED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetPost_permanentlyBlocked_resetsToDraftAndRecordsModeration() {
        Post post = newPost(PostStatus.ARCHIVED);
        post.setRejectionCount(3);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(admin));

        AdminPostResponse result = adminService.resetPost(1, 1, "Unlocked, try again");

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(post.getRejectionCount()).isEqualTo(0);
        assertThat(post.getModerationNote()).isEqualTo("Unlocked, try again");
        assertThat(post.getModeratedBy()).isEqualTo(admin);
        verify(postRepository).save(post);
    }

    @Test
    void resetPost_notPermanentlyBlocked_throwsIllegalState() {
        Post post = newPost(PostStatus.REJECTED);
        post.setRejectionCount(1);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> adminService.resetPost(1, 1, "note"))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void resetPost_postNotFound_throwsResourceNotFound() {
        given(postRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.resetPost(1, 99, "note"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_existingPost_deletesImageThenPost() {
        Post post = newPost(PostStatus.DRAFT);
        post.setCoverImage("cover.jpg");
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(admin));

        adminService.delete(1, 1);

        InOrder order = inOrder(storageService, postRepository);
        order.verify(storageService).delete("cover.jpg");
        order.verify(postRepository).delete(post);
        verify(adminActionLogService).record(1, admin.getEmail(), "POST_DELETE", "POST", 1, "title=Title");
    }

    @Test
    void updateStatus_permanentlyBlockedPost_throwsIllegalState_andDoesNotSave() {
        Post post = newPost(PostStatus.ARCHIVED);
        post.setRejectionCount(3);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> adminService.updateStatus(1, 1, new AdminStatusUpdateRequest("PUBLISHED", null)))
                .isInstanceOf(IllegalStateException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void updateStatus_permanentlyBlockedPost_rejectingAgainAlsoBlocked() {
        Post post = newPost(PostStatus.ARCHIVED);
        post.setRejectionCount(3);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> adminService.updateStatus(1, 1, new AdminStatusUpdateRequest("REJECTED", "note")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(post.getRejectionCount()).isEqualTo(3);
        verify(postRepository, never()).save(any());
    }
}
