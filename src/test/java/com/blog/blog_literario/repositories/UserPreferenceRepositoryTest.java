package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.model.UserPreferenceEntry;

@DataJpaTest
@ActiveProfiles("test")
class UserPreferenceRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired UserPreferenceRepository preferenceRepository;

    private User alice;

    @BeforeEach
    void setUp() {
        Role reader = em.persist(new Role("READER"));
        User user = new User();
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPassword("encoded");
        user.setRole(reader);
        alice = em.persist(user);
    }

    @Test
    void saveAndFindByUserIdAndPrefKey_roundTrips() {
        preferenceRepository.save(new UserPreferenceEntry(
                alice.getId(), "notifications.post-moderation", "false"));
        em.flush();
        em.clear();

        Optional<UserPreferenceEntry> found = preferenceRepository
                .findByUserIdAndPrefKey(alice.getId(), "notifications.post-moderation");

        assertThat(found).isPresent();
        assertThat(found.get().getValue()).isEqualTo("false");
    }

    @Test
    void save_sameUserAndKey_updatesInsteadOfDuplicating() {
        preferenceRepository.save(new UserPreferenceEntry(
                alice.getId(), "notifications.post-moderation", "false"));
        em.flush();
        preferenceRepository.save(new UserPreferenceEntry(
                alice.getId(), "notifications.post-moderation", "true"));
        em.flush();
        em.clear();

        List<UserPreferenceEntry> all = preferenceRepository.findByUserId(alice.getId());

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getValue()).isEqualTo("true");
    }

    @Test
    void findByUserId_noRows_returnsEmptyList() {
        assertThat(preferenceRepository.findByUserId(alice.getId())).isEmpty();
    }
}
