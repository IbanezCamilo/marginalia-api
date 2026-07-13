package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    private Role readerRole;
    private Role authorRole;

    @BeforeEach
    void setUp() {
        readerRole = roleRepository.save(new Role("READER"));
        authorRole = roleRepository.save(new Role("AUTHOR"));
    }

    @Test
    void searchByNameOrEmail_matchByName_returnsUser() {
        persistUser("Alice", "alice@example.com", readerRole);
        persistUser("Bob", "bob@example.com", readerRole);

        Page<User> result = userRepository.searchByNameOrEmail("ali", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Alice");
    }

    @Test
    void searchByNameOrEmail_matchByEmail_returnsUser() {
        persistUser("Alice", "alice@example.com", readerRole);
        persistUser("Bob", "bob@example.com", readerRole);

        Page<User> result = userRepository.searchByNameOrEmail("bob@example", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Bob");
    }

    @Test
    void searchByNameOrEmail_caseInsensitive_returnsUser() {
        persistUser("Alice", "alice@example.com", readerRole);

        Page<User> result = userRepository.searchByNameOrEmail("ALICE", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void searchByNameOrEmail_noMatch_returnsEmptyPage() {
        persistUser("Alice", "alice@example.com", readerRole);

        Page<User> result = userRepository.searchByNameOrEmail("zzz", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void findByRoleName_returnsOnlyMatchingRole() {
        persistUser("Alice", "alice@example.com", authorRole);
        persistUser("Bob", "bob@example.com", readerRole);

        Page<User> result = userRepository.findByRoleName("AUTHOR", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Alice");
    }

    @Test
    void existsByEmailExcludingId_emailBelongsToOtherUser_returnsTrue() {
        User alice = persistUser("Alice", "alice@example.com", readerRole);
        User bob = persistUser("Bob", "bob@example.com", readerRole);
        em.flush();

        boolean result = userRepository.existsByEmailExcludingId(bob.getEmail(), alice.getId());

        assertThat(result).isTrue();
    }

    @Test
    void existsByEmailExcludingId_ownEmail_returnsFalse() {
        User alice = persistUser("Alice", "alice@example.com", readerRole);
        em.flush();

        boolean result = userRepository.existsByEmailExcludingId(alice.getEmail(), alice.getId());

        assertThat(result).isFalse();
    }

    private User persistUser(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setProfilePicture("");
        user.setRole(role);
        return em.persist(user);
    }
}
