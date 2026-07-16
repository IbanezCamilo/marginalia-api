package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.blog.blog_literario.dto.posts.PostCatalogSort;
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

    @Test
    void countByCategoryId_returnsNumberOfPostsInCategory() {
        persistPost("Post One", "post-one", PostStatus.DRAFT, category);
        persistPost("Post Two", "post-two", PostStatus.DRAFT, category);
        em.flush();

        assertThat(postRepository.countByCategoryId(category.getId())).isEqualTo(2L);
    }

    @Test
    void countByCategoryId_noPosts_returnsZero() {
        assertThat(postRepository.countByCategoryId(category.getId())).isZero();
    }

    @Test
    void clearModeratedByForUser_nullsOutReferenceOnAllModeratedPosts() {
        Role adminRole = roleRepository.save(new Role("ADMIN"));
        User moderatorUser = new User();
        moderatorUser.setName("Mod");
        moderatorUser.setEmail("mod@test.com");
        moderatorUser.setPassword("hashed");
        moderatorUser.setProfilePicture("");
        moderatorUser.setRole(adminRole);
        em.persist(moderatorUser);

        Post post = persistPost("Reviewed Post", "reviewed-post", PostStatus.PUBLISHED, category);
        post.recordModeration(moderatorUser, "Looks good");
        em.persist(post);
        em.flush();

        postRepository.clearModeratedByForUser(moderatorUser.getId());
        em.flush();
        em.clear();

        Post reloaded = postRepository.findById(post.getId()).orElseThrow();
        assertThat(reloaded.getModeratedBy()).isNull();
    }

    @Test
    void findByStatus_withFeaturedSort_ordersFeaturedFirstThenByRecency() {
        Post featuredOld = persistPost("Featured Old", "featured-old", PostStatus.PUBLISHED, category);
        featuredOld.setFeatured(true);
        Post featuredNew = persistPost("Featured New", "featured-new", PostStatus.PUBLISHED, category);
        featuredNew.setFeatured(true);
        Post regularNewest = persistPost("Regular Newest", "regular-newest", PostStatus.PUBLISHED, category);
        Post featuredDraft = persistPost("Featured Draft", "featured-draft", PostStatus.DRAFT, category);
        featuredDraft.setFeatured(true);
        em.flush();

        // created_at is @PrePersist-managed and not updatable through the entity,
        // so pin deterministic timestamps directly to make the ordering unambiguous.
        setCreatedAt(featuredOld.getId(), LocalDateTime.of(2026, 1, 1, 12, 0));
        setCreatedAt(featuredNew.getId(), LocalDateTime.of(2026, 1, 2, 12, 0));
        setCreatedAt(regularNewest.getId(), LocalDateTime.of(2026, 1, 3, 12, 0));
        setCreatedAt(featuredDraft.getId(), LocalDateTime.of(2026, 1, 4, 12, 0));
        em.clear();

        Page<Post> result = postRepository.findByStatus(
                PostStatus.PUBLISHED,
                PageRequest.of(0, 10, PostCatalogSort.FEATURED.toSort()));

        assertThat(result.getContent())
                .extracting(Post::getSlug)
                .containsExactly("featured-new", "featured-old", "regular-newest");
    }

    private void setCreatedAt(Integer postId, LocalDateTime createdAt) {
        em.getEntityManager()
                .createNativeQuery("update posts set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", postId)
                .executeUpdate();
    }

    private Post persistPost(String title, String slug, PostStatus status, Category cat) {
        Post post = new Post(title, "Content body", status, slug, author, cat);
        return em.persist(post);
    }
}
