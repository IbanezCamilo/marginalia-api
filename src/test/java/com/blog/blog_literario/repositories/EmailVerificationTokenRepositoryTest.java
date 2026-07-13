package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.blog.blog_literario.model.EmailVerificationToken;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;

@DataJpaTest
@ActiveProfiles("test")
class EmailVerificationTokenRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired EmailVerificationTokenRepository tokenRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        Role reader = em.persist(new Role("READER"));
        alice = em.persist(buildUser("Alice", "alice@example.com", reader));
        bob = em.persist(buildUser("Bob", "bob@example.com", reader));
    }

    private User buildUser(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("encoded");
        user.setRole(role);
        return user;
    }

    private EmailVerificationToken persistToken(User user, String hash,
                                                LocalDateTime createdAt, LocalDateTime expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(hash);
        token.setUser(user);
        token.setCreatedAt(createdAt);
        token.setExpiresAt(expiresAt);
        return em.persist(token);
    }

    @Test
    void findByToken_returnsMatchingToken() {
        persistToken(alice, "hash-1", LocalDateTime.now(), LocalDateTime.now().plusHours(24));

        Optional<EmailVerificationToken> result = tokenRepository.findByToken("hash-1");

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findTopByUserOrderByCreatedAtDesc_returnsNewestToken() {
        persistToken(alice, "hash-old", LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(3));
        persistToken(alice, "hash-new", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(24));
        persistToken(bob, "hash-bob", LocalDateTime.now(), LocalDateTime.now().plusHours(24));

        Optional<EmailVerificationToken> result = tokenRepository.findTopByUserOrderByCreatedAtDesc(alice);

        assertThat(result).isPresent();
        assertThat(result.get().getToken()).isEqualTo("hash-new");
    }

    @Test
    void countByUserAndCreatedAtAfter_countsOnlyRecentTokensOfThatUser() {
        persistToken(alice, "hash-recent-1", LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(2));
        persistToken(alice, "hash-recent-2", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(24));
        persistToken(alice, "hash-ancient", LocalDateTime.now().minusHours(30), LocalDateTime.now().minusHours(6));
        persistToken(bob, "hash-bob", LocalDateTime.now(), LocalDateTime.now().plusHours(24));

        long count = tokenRepository.countByUserAndCreatedAtAfter(alice, LocalDateTime.now().minusHours(24));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void deleteByExpiresAtBefore_removesOnlyStaleRows() {
        persistToken(alice, "hash-stale", LocalDateTime.now().minusHours(50), LocalDateTime.now().minusHours(26));
        persistToken(alice, "hash-active", LocalDateTime.now(), LocalDateTime.now().plusHours(24));

        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusHours(24));
        em.flush();
        em.clear();

        assertThat(tokenRepository.findByToken("hash-stale")).isEmpty();
        assertThat(tokenRepository.findByToken("hash-active")).isPresent();
    }

    @Test
    void deleteByUser_removesAllTokensOfThatUserOnly() {
        persistToken(alice, "hash-a1", LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(1));
        persistToken(alice, "hash-a2", LocalDateTime.now(), LocalDateTime.now().plusHours(24));
        persistToken(bob, "hash-b1", LocalDateTime.now(), LocalDateTime.now().plusHours(24));

        tokenRepository.deleteByUser(alice);
        em.flush();
        em.clear();

        assertThat(tokenRepository.findByToken("hash-a1")).isEmpty();
        assertThat(tokenRepository.findByToken("hash-a2")).isEmpty();
        assertThat(tokenRepository.findByToken("hash-b1")).isPresent();
    }
}
