package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.blog.blog_literario.model.Category;

@DataJpaTest
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        em.persist(new Category("Fiction", "fiction"));
        em.flush();
    }

    @Test
    void findByName_existingName_returnsCategory() {
        Optional<Category> result = categoryRepository.findByName("Fiction");

        assertThat(result).isPresent();
        assertThat(result.get().getSlug()).isEqualTo("fiction");
    }

    @Test
    void findByName_nonExistentName_returnsEmpty() {
        Optional<Category> result = categoryRepository.findByName("Nonfiction");

        assertThat(result).isEmpty();
    }

    @Test
    void findBySlug_existingSlug_returnsCategory() {
        Optional<Category> result = categoryRepository.findBySlug("fiction");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Fiction");
    }

    @Test
    void existsBySlug_existingSlug_returnsTrue() {
        boolean result = categoryRepository.existsBySlug("fiction");

        assertThat(result).isTrue();
    }

    @Test
    void existsBySlug_nonExistentSlug_returnsFalse() {
        boolean result = categoryRepository.existsBySlug("nonfiction");

        assertThat(result).isFalse();
    }
}
