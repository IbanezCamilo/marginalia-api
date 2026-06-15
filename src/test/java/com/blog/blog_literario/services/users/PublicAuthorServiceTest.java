package com.blog.blog_literario.services.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
import com.blog.blog_literario.services.images.StorageService;

@ExtendWith(MockitoExtension.class)
class PublicAuthorServiceTest {

    @Mock UserRepository userRepository;
    @Mock PostRepository postRepository;
    @Mock StorageService storageService;
    @Mock AvatarResolver avatarResolver;

    @InjectMocks PublicAuthorService publicAuthorService;

    private final Pageable pageable = PageRequest.of(0, 10);

    private User author() {
        User author = new User(1, "Alice", "alice@test.com", new Role(Role.AUTHOR));
        author.setDescription("Bio");
        author.setProfilePicture("avatar.jpg");
        return author;
    }

    @Test
    void getAuthorById_existing_returnsPublicAuthorResponse() {
        given(userRepository.findById(1)).willReturn(Optional.of(author()));
        given(avatarResolver.resolve("avatar.jpg", "Alice")).willReturn("https://avatar-url");

        PublicAuthorResponse result = publicAuthorService.getAuthorById(1);

        assertThat(result.id()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.description()).isEqualTo("Bio");
        assertThat(result.profilePicture()).isEqualTo("https://avatar-url");
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
        given(postRepository.findByAuthorIdAndStatus(1, PostStatus.PUBLISHED, pageable))
                .willReturn(new PageImpl<>(List.of(post), pageable, 1));

        Page<PublicPostResponse> result = publicAuthorService.getPublishedPostsByAuthor(1, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).slug()).isEqualTo("my-post");
    }

    @Test
    void getPublishedPostsByAuthor_nonExistentAuthor_throwsResourceNotFoundException() {
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicAuthorService.getPublishedPostsByAuthor(99, pageable))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(postRepository, never()).findByAuthorIdAndStatus(any(), any(), any());
    }
}
