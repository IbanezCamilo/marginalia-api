package com.blog.blog_literario.dto.posts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostCatalogFilterTest {

    @Test
    void of_normalizesQ_trimsAndDropsShortOrOverlongValues() {
        assertThat(PostCatalogFilter.of(null, null, null, null, "  borges  ").q()).isEqualTo("borges");
        assertThat(PostCatalogFilter.of(null, null, null, null, "a").q()).isNull();
        assertThat(PostCatalogFilter.of(null, null, null, null, "   ").q()).isNull();
        assertThat(PostCatalogFilter.of(null, null, null, null, null).q()).isNull();
        assertThat(PostCatalogFilter.of(null, null, null, null, "x".repeat(150)).q()).hasSize(100);
    }

    @Test
    void of_parsesTimeLeniently() {
        assertThat(PostCatalogFilter.of(null, null, null, "short", null).time())
                .isEqualTo(ReadingTimeBucket.SHORT);
        assertThat(PostCatalogFilter.of(null, null, null, "bogus", null).time()).isNull();
    }

    @Test
    void of_passesIdentityFiltersThrough() {
        PostCatalogFilter f = PostCatalogFilter.of("ficcion", 3, "7", null, null);
        assertThat(f.categorySlug()).isEqualTo("ficcion");
        assertThat(f.categoryId()).isEqualTo(3);
        assertThat(f.authorId()).isEqualTo(7);
    }

    @Test
    void of_parsesAuthorIdLeniently() {
        assertThat(PostCatalogFilter.of(null, null, "7", null, null).authorId()).isEqualTo(7);
        assertThat(PostCatalogFilter.of(null, null, "7x", null, null).authorId()).isNull();
        assertThat(PostCatalogFilter.of(null, null, null, null, null).authorId()).isNull();
    }
}
