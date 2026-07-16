package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.blog.blog_literario.dto.posts.ReadingTimeBucket;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class PostCatalogSpecificationsTest {

    @Autowired TestEntityManager em;
    @Autowired PostRepository postRepository;

    private User alice;
    private User bruno;
    private Category ficcion;
    private Category poesia;

    @BeforeEach
    void seed() {
        Role reader = em.persist(new Role(Role.READER));
        alice = em.persist(user("Alice Munro", "alice@example.com", reader));
        bruno = em.persist(user("Bruno Schulz", "bruno@example.com", reader));
        ficcion = em.persist(category("Ficción", "ficcion"));
        poesia = em.persist(category("Poesía", "poesia"));

        // slug, author, category, status, wordCount
        post("cafe-corto", alice, ficcion, PostStatus.PUBLISHED, 500);      // SHORT
        post("pausa-media", alice, poesia, PostStatus.PUBLISHED, 801);     // MEDIUM lower edge
        post("sobremesa-larga", bruno, ficcion, PostStatus.PUBLISHED, 3001); // LONG lower edge
        post("borrador-oculto", alice, ficcion, PostStatus.DRAFT, 100);    // never visible
        em.flush();
    }

    @Test
    void isPublished_excludesDrafts() {
        List<Post> result = postRepository.findAll(
                Specification.allOf(PostCatalogSpecifications.isPublished()));
        assertThat(result).extracting(Post::getSlug)
                .containsExactlyInAnyOrder("cafe-corto", "pausa-media", "sobremesa-larga");
    }

    @Test
    void facetsStack_categoryPlusTimePlusAuthor() {
        List<Post> result = postRepository.findAll(Specification.allOf(
                PostCatalogSpecifications.isPublished(),
                PostCatalogSpecifications.hasCategorySlug("ficcion"),
                PostCatalogSpecifications.readingTimeIn(ReadingTimeBucket.LONG),
                PostCatalogSpecifications.hasAuthor(bruno.getId())));
        assertThat(result).extracting(Post::getSlug).containsExactly("sobremesa-larga");
    }

    @Test
    void readingTimeBuckets_partitionOnExactWordEdges() {
        assertThat(published(PostCatalogSpecifications.readingTimeIn(ReadingTimeBucket.SHORT)))
                .containsExactly("cafe-corto");
        assertThat(published(PostCatalogSpecifications.readingTimeIn(ReadingTimeBucket.MEDIUM)))
                .containsExactly("pausa-media");
        assertThat(published(PostCatalogSpecifications.readingTimeIn(ReadingTimeBucket.LONG)))
                .containsExactly("sobremesa-larga");
    }

    @Test
    void matchesQuery_hitsTitleOrAuthorNameCaseInsensitive() {
        // "bruno" matches the author name of sobremesa-larga, not any title
        assertThat(published(PostCatalogSpecifications.matchesQuery("BRUNO")))
                .containsExactly("sobremesa-larga");
        // "pausa" matches a title
        assertThat(published(PostCatalogSpecifications.matchesQuery("pausa")))
                .containsExactly("pausa-media");
    }

    @Test
    void nullInputs_meanNoFilter() {
        assertThat(PostCatalogSpecifications.hasCategorySlug(null)).isNull();
        assertThat(PostCatalogSpecifications.hasCategorySlug("  ")).isNull();
        assertThat(PostCatalogSpecifications.hasCategory(null)).isNull();
        assertThat(PostCatalogSpecifications.hasAuthor(null)).isNull();
        assertThat(PostCatalogSpecifications.readingTimeIn(null)).isNull();
        assertThat(PostCatalogSpecifications.matchesQuery(null)).isNull();
        assertThat(PostCatalogSpecifications.matchesQuery(" ")).isNull();
    }

    private List<String> published(Specification<Post> facet) {
        return postRepository.findAll(
                        Specification.allOf(PostCatalogSpecifications.isPublished(), facet))
                .stream().map(Post::getSlug).toList();
    }

    private User user(String name, String email, Role role) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword("x");
        u.setRole(role);
        u.setEmailVerified(true);
        return u;
    }

    private Category category(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        return c;
    }

    private Post post(String slug, User author, Category cat, PostStatus status, int words) {
        Post p = new Post("Titulo " + slug, "{\"type\":\"doc\",\"content\":[]}", status, slug, author, cat);
        p.setWordCount(words);
        return em.persist(p);
    }
}
