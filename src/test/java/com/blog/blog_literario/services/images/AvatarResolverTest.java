package com.blog.blog_literario.services.images;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvatarResolverTest {

    @Mock
    StorageService storageService;

    private AvatarResolver avatarResolver;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        avatarResolver = new AvatarResolver(storageService);
    }

    @Test
    void resolve_withStoredImage_returnsStorageServiceUrl() {
        given(storageService.buildUrl("photo.jpg")).willReturn("http://localhost:8080/api/images/photo.jpg");

        String result = avatarResolver.resolve("photo.jpg", "Alice");

        assertThat(result).isEqualTo("http://localhost:8080/api/images/photo.jpg");
    }

    @Test
    void resolve_nullFileName_returnsUiAvatarsFallbackUrl() {
        String result = avatarResolver.resolve(null, "Alice");

        assertThat(result).isEqualTo(
                "https://ui-avatars.com/api/?name=Alice&background=0c0a09&color=fafaf9&bold=true&size=128&rounded=true");
        verifyNoInteractions(storageService);
    }

    @Test
    void resolve_blankFileName_returnsUiAvatarsFallbackUrl() {
        String result = avatarResolver.resolve("   ", "Alice");

        assertThat(result).isEqualTo(
                "https://ui-avatars.com/api/?name=Alice&background=0c0a09&color=fafaf9&bold=true&size=128&rounded=true");
        verifyNoInteractions(storageService);
    }

    @Test
    void resolve_nameWithSpecialCharacters_urlEncodesName() {
        String result = avatarResolver.resolve(null, "José Pérez");

        assertThat(result).isEqualTo(
                "https://ui-avatars.com/api/?name=Jos%C3%A9+P%C3%A9rez&background=0c0a09&color=fafaf9&bold=true&size=128&rounded=true");
    }
}
