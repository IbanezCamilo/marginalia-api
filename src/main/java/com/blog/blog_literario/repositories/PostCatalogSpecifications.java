package com.blog.blog_literario.repositories;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.blog.blog_literario.dto.posts.ReadingTimeBucket;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;

/**
 * Composable predicates for the public catalog — one per facet, designed to stack.
 *
 * <p>Every factory returns {@code null} for a null/blank input, and callers combine them
 * with {@link Specification#allOf}, which skips nulls: an absent facet simply doesn't
 * constrain the query. Adding a catalog facet means adding ONE factory here plus its
 * request parameter — nothing else in the query path changes.
 */
public final class PostCatalogSpecifications {

    private PostCatalogSpecifications() {
    }

    /**
     * Base predicate for every catalog query. Also fetch-joins author and category on
     * result queries (not count queries) to keep the DTO mapping free of N+1 selects,
     * matching what {@code @EntityGraph} did on the old derived methods.
     */
    public static Specification<Post> isPublished() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("author");
                root.fetch("category");
            }
            return cb.equal(root.get("status"), PostStatus.PUBLISHED);
        };
    }

    public static Specification<Post> hasCategorySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("slug"), slug.trim());
    }

    /** Legacy id-based filter; kept so pre-existing API clients don't break. */
    public static Specification<Post> hasCategory(Integer categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Post> hasAuthor(Integer authorId) {
        if (authorId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("author").get("id"), authorId);
    }

    /** Range over the persisted {@code word_count}; bounds come from the bucket (single definition). */
    public static Specification<Post> readingTimeIn(ReadingTimeBucket bucket) {
        if (bucket == null) {
            return null;
        }
        return (root, query, cb) -> {
            var words = root.<Integer>get("wordCount");
            if (bucket.maxWords() == null) {
                return cb.greaterThanOrEqualTo(words, bucket.minWords());
            }
            return cb.between(words, bucket.minWords(), bucket.maxWords());
        };
    }

    /**
     * Search predicate — deliberately isolated and replaceable. Today it matches title
     * OR author name (case-insensitive substring). When a persisted plain-text content
     * column exists, it joins this OR as another source; nothing outside this method changes.
     */
    public static Specification<Post> matchesQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("author").get("name")), pattern));
    }
}
