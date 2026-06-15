package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.blog.blog_literario.model.Role;

@DataJpaTest
@ActiveProfiles("test")
class RoleRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        em.persist(new Role("READER"));
        em.flush();
    }

    @Test
    void findByName_existingRole_returnsRole() {
        Optional<Role> result = roleRepository.findByName("READER");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("READER");
    }

    @Test
    void findByName_nonExistentRole_returnsEmpty() {
        Optional<Role> result = roleRepository.findByName("ADMIN");

        assertThat(result).isEmpty();
    }
}
