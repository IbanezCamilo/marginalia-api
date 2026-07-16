package com.blog.blog_literario.services.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import com.blog.blog_literario.dto.posts.CreatePostRequest;
import com.blog.blog_literario.dto.posts.UpdatePostRequest;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyPostCommandServiceWordCountTest {

    @Mock StorageService storageService;
    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;
    @Mock CategoryRepository categoryRepository;
    @InjectMocks MyPostCommandService service;

    // 6 words of prose inside a valid TipTap doc
    private static final String SIX_WORD_DOC =
            "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":"
            + "[{\"type\":\"text\",\"text\":\"uno dos tres cuatro cinco seis\"}]}]}";

    @Test
    void create_setsWordCountFromContent() {
        User user = new User();
        user.setId(1);
        user.setName("Alice");
        given(userRepository.findById(1)).willReturn(Optional.of(user));

        // CreatePostRequest component order: title, content, categoryId, status, focalX, focalY
        CreatePostRequest request = new CreatePostRequest(
                "Un titulo valido", SIX_WORD_DOC, null, "DRAFT", null, null);

        service.create(1, request);

        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(saved.capture());
        assertThat(saved.getValue().getWordCount()).isEqualTo(6);
    }

    @Test
    void update_recomputesWordCountWhenContentChanges() {
        User user = new User();
        user.setId(1);
        user.setName("Alice");
        Post existing = new Post("Viejo titulo largo", "{\"type\":\"doc\",\"content\":[]}",
                PostStatus.DRAFT, "viejo-titulo-largo", user, null);
        existing.setWordCount(99);
        given(postRepository.findByIdAndAuthorId(5, 1)).willReturn(Optional.of(existing));
        given(postRepository.existsBySlugAndIdNot(any(), any())).willReturn(false);

        // UpdatePostRequest component order: title, content, categoryId, status, focalX, focalY
        UpdatePostRequest request = new UpdatePostRequest(
                "Nuevo titulo largo", SIX_WORD_DOC, null, "DRAFT", null, null);

        service.update(1, 5, request);

        assertThat(existing.getWordCount()).isEqualTo(6);
    }
}
