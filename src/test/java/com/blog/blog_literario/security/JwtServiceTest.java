package com.blog.blog_literario.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blog.blog_literario.config.properties.JwtProperties;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;

import io.jsonwebtoken.ExpiredJwtException;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0cy0xMjM0NTY3ODkw";

    private JwtService jwtService;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 86_400_000L, 604_800_000L));
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.AUTHOR));
        userDetails = new UserDetailsImpl(user);
    }

    @Test
    void generateToken_returnsNonNullSignedToken() {
        String token = jwtService.generateToken(userDetails);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractUsername_returnsSubjectFromToken() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice@test.com");
    }

    @Test
    void isTokenValid_matchingUserAndNotExpired_returnsTrue() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_usernameMismatch_returnsFalse() {
        String token = jwtService.generateToken(userDetails);
        User otherUser = new User(2, "Bob", "bob@test.com", new Role(Role.READER));
        UserDetailsImpl otherUserDetails = new UserDetailsImpl(otherUser);

        assertThat(jwtService.isTokenValid(token, otherUserDetails)).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_throwsExpiredJwtException() {
        JwtService shortLivedJwtService = new JwtService(new JwtProperties(SECRET, -1000L, 604_800_000L));
        String token = shortLivedJwtService.generateToken(userDetails);

        assertThatThrownBy(() -> shortLivedJwtService.isTokenValid(token, userDetails))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractClaim_withCustomResolver_extractsArbitraryClaim() {
        String token = jwtService.generateToken(userDetails);

        String subject = jwtService.extractClaim(token, claims -> claims.getSubject());

        assertThat(subject).isEqualTo("alice@test.com");
    }

    @Test
    void generateToken_withExtraClaims_embedsClaimsInToken() {
        Map<String, Object> extraClaims = Map.of("role", "AUTHOR");

        String token = jwtService.generateToken(extraClaims, userDetails);

        String role = jwtService.extractClaim(token, claims -> claims.get("role", String.class));
        assertThat(role).isEqualTo("AUTHOR");
    }
}
