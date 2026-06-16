package com.blog.blog_literario.services.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import com.blog.blog_literario.config.properties.JwtProperties;
import com.blog.blog_literario.model.RefreshToken;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtProperties jwtProperties;

    @InjectMocks RefreshTokenService refreshTokenService;

    private User testUser() {
        return new User(1, "Alice", "alice@test.com", new Role(Role.READER));
    }

    @Test
    void create_savesHashedTokenAndReturnsRaw() {
        given(jwtProperties.refreshExpiration()).willReturn(604800000L);

        String rawToken = refreshTokenService.create(testUser());

        assertThat(rawToken).isNotBlank().hasSize(32); // UUID without dashes
        verify(refreshTokenRepository).save(argThat(rt ->
                rt.getToken().equals(refreshTokenService.hashToken(rawToken))
                && rt.getUser().getEmail().equals("alice@test.com")
                && rt.getExpiresAt() != null
        ));
    }

    @Test
    void create_storedTokenIsHashedNotRaw() {
        given(jwtProperties.refreshExpiration()).willReturn(604800000L);

        String rawToken = refreshTokenService.create(testUser());
        String expectedHash = refreshTokenService.hashToken(rawToken);

        verify(refreshTokenRepository).save(argThat(rt ->
                rt.getToken().equals(expectedHash) && !rt.getToken().equals(rawToken)
        ));
    }

    @Test
    void rotate_validToken_deletesOldAndCreatesNew() {
        given(jwtProperties.refreshExpiration()).willReturn(604800000L);
        User user = testUser();
        String rawToken = "abcdef1234567890abcdef1234567890";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken stored = new RefreshToken();
        stored.setToken(hash);
        stored.setUser(user);
        stored.setExpiresAt(LocalDateTime.now().plusDays(7));

        given(refreshTokenRepository.findByToken(hash)).willReturn(Optional.of(stored));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawToken);

        verify(refreshTokenRepository).delete(stored);
        assertThat(result.user()).isEqualTo(user);
        assertThat(result.newRawToken()).isNotBlank().isNotEqualTo(rawToken);
    }

    @Test
    void rotate_tokenNotFound_throwsBadCredentials() {
        String rawToken = "nonexistent";
        given(refreshTokenRepository.findByToken(refreshTokenService.hashToken(rawToken)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotate_expiredToken_deletesAndThrowsBadCredentials() {
        String rawToken = "expiredtoken00000000000000000000";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken expired = new RefreshToken();
        expired.setToken(hash);
        expired.setUser(testUser());
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));

        given(refreshTokenRepository.findByToken(hash)).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    void deleteByRawToken_existingToken_deletesIt() {
        String rawToken = "deletetoken00000000000000000000a";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken entity = new RefreshToken();
        entity.setToken(hash);
        entity.setUser(testUser());
        entity.setExpiresAt(LocalDateTime.now().plusDays(1));

        given(refreshTokenRepository.findByToken(hash)).willReturn(Optional.of(entity));

        refreshTokenService.deleteByRawToken(rawToken);

        verify(refreshTokenRepository).delete(entity);
    }

    @Test
    void deleteByRawToken_tokenNotFound_doesNothing() {
        String rawToken = "unknowntoken0000000000000000000a";
        given(refreshTokenRepository.findByToken(any())).willReturn(Optional.empty());

        refreshTokenService.deleteByRawToken(rawToken);

        verify(refreshTokenRepository, org.mockito.Mockito.never()).delete(any(RefreshToken.class));
    }

    @Test
    void create_deletesAllUserTokensBeforeSavingNew() {
        given(jwtProperties.refreshExpiration()).willReturn(604800000L);
        User user = testUser();

        refreshTokenService.create(user);

        InOrder order = inOrder(refreshTokenRepository);
        order.verify(refreshTokenRepository).deleteByUser(user);
        order.verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
}
