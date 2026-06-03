package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class PostRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired PostRepository postRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired CategoryRepository categoryRepository;

    private User author;
    private Category category;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.save(new Role("AUTHOR"));

        User u = new User();
        u.setName("Alice");
        u.setEmail("alice@test.com");
        u.setPassword("hashed");
        u.setProfilePicture("");
        u.setRole(role);
        author = em.persist(u);

        Category cat = new Category();
        cat.setName("Fiction");
        cat.setSlug("fiction");
        category = em.persist(cat);

        em.flush();
    }

    @Test
    void findByCategoryIdAndStatus_returnsOnlyMatching() {
        persistPost("Published Post", "published-post", PostStatus.PUBLISHED, category);
        persistPost("Draft Post", "draft-post", PostStatus.DRAFT, category);

        Page<Post> result = postRepository.findByCategoryIdAndStatus(
                category.getId(), PostStatus.PUBLISHED, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSlug()).isEqualTo("published-post");
    }

    @Test
    void findByStatusIn_excludesNonMatchingStatus() {
        persistPost("Draft", "draft-slug", PostStatus.DRAFT, category);
        persistPost("Published", "published-slug", PostStatus.PUBLISHED, category);
        persistPost("Archived", "archived-slug", PostStatus.ARCHIVED, category);

        Page<Post> result = postRepository.findByStatusIn(
                List.of(PostStatus.DRAFT, PostStatus.PUBLISHED), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Post::getStatus)
                .doesNotContain(PostStatus.ARCHIVED);
    }

    @Test
    void existsBySlugAndIdNot_slugBelongsToOtherPost_returnsTrue() {
        Post post1 = persistPost("Post One", "post-one", PostStatus.DRAFT, category);
        Post post2 = persistPost("Post Two", "post-two", PostStatus.DRAFT, category);
        em.flush();

        boolean result = postRepository.existsBySlugAndIdNot(post2.getSlug(), post1.getId());

        assertThat(result).isTrue();
    }

    @Test
    void existsBySlugAndIdNot_ownSlug_returnsFalse() {
        Post post = persistPost("My Post", "my-post", PostStatus.DRAFT, category);
        em.flush();

        boolean result = postRepository.existsBySlugAndIdNot(post.getSlug(), post.getId());

        assertThat(result).isFalse();
    }

    @Test
    void findAllByAuthorId_returnsOnlyAuthorPosts() {
        Role role2 = roleRepository.save(new Role("READER"));
        User other = new User();
        other.setName("Bob");
        other.setEmail("bob@test.com");
        other.setPassword("hashed");
        other.setProfilePicture("");
        other.setRole(role2);
        User otherAuthor = em.persist(other);
        em.flush();

        persistPost("Alice Post", "alice-post", PostStatus.DRAFT, category);
        Post bobPost = new Post("Bob Post", "Content", PostStatus.DRAFT, "bob-post", otherAuthor, category);
        em.persist(bobPost);
        em.flush();

        List<Post> result = postRepository.findAllByAuthorId(author.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSlug()).isEqualTo("alice-post");
    }

    @Test
    void findPublishedBySlug_publishedPost_returnsPost() {
        persistPost("Published Post", "pub-slug", PostStatus.PUBLISHED, category);

        Optional<Post> result = postRepository.findPublishedBySlug("pub-slug");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void findPublishedBySlug_draftPost_returnsEmpty() {
        persistPost("Draft Post", "draft-only-slug", PostStatus.DRAFT, category);

        Optional<Post> result = postRepository.findPublishedBySlug("draft-only-slug");

        assertThat(result).isEmpty();
    }

    private Post persistPost(String title, String slug, PostStatus status, Category cat) {
        Post post = new Post(title, "Content body", status, slug, author, cat);
        return em.persist(post);
    }
}
