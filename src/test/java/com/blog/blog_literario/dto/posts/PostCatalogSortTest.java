package com.blog.blog_literario.dto.posts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

class PostCatalogSortTest {

    @Test
    void featured_ordersByFeaturedDescThenCreatedAtDesc() {
        Sort sort = PostCatalogSort.FEATURED.toSort();

        assertThat(sort).containsExactly(
                Sort.Order.desc("featured"),
                Sort.Order.desc("createdAt"));
    }

    @ParameterizedTest
    @CsvSource({
            "featured,   FEATURED",
            "recent,     RECENT",
            "oldest,     OLDEST",
            "title_asc,  TITLE_ASC",
            "title_desc, TITLE_DESC",
    })
    void from_namedKey_resolvesToEnumValue(String key, PostCatalogSort expected) {
        assertThat(PostCatalogSort.from(key)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "'createdAt,desc', RECENT",
            "'createdAt,asc',  OLDEST",
            "'title,asc',      TITLE_ASC",
            "'title,desc',     TITLE_DESC",
    })
    void from_legacyRawSortString_mapsToEquivalent(String key, PostCatalogSort expected) {
        assertThat(PostCatalogSort.from(key)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FEATURED", "Recent", "TITLE_ASC", " featured "})
    void from_isCaseInsensitiveAndTrimmed(String key) {
        assertThat(PostCatalogSort.from(key)).isNotNull();
        assertThat(PostCatalogSort.from("Recent")).isEqualTo(PostCatalogSort.RECENT);
        assertThat(PostCatalogSort.from(" featured ")).isEqualTo(PostCatalogSort.FEATURED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "bogus", "moderationNote,desc", "author.email"})
    void from_nullBlankOrUnknown_fallsBackToFeatured(String key) {
        assertThat(PostCatalogSort.from(key)).isEqualTo(PostCatalogSort.FEATURED);
    }
}
