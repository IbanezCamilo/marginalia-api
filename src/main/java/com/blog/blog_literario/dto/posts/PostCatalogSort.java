package com.blog.blog_literario.dto.posts;

import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Whitelisted sort orders for the public post catalog ({@code GET /api/public/posts?sort=...}).
 *
 * <p>The public endpoint accepts only these named keys instead of raw Spring Data sort
 * expressions, so clients cannot order the feed by non-public entity fields.
 * {@link #FEATURED} is the default: editorially curated posts first (newest first among
 * them), then the rest by recency — with nothing featured it degenerates to pure recency.
 */
public enum PostCatalogSort {
    FEATURED(Sort.by(Sort.Order.desc("featured"), Sort.Order.desc("createdAt"))),
    RECENT(Sort.by(Sort.Order.desc("createdAt"))),
    OLDEST(Sort.by(Sort.Order.asc("createdAt"))),
    TITLE_ASC(Sort.by(Sort.Order.asc("title"))),
    TITLE_DESC(Sort.by(Sort.Order.desc("title")));

    /** Raw Spring Data sort strings the frontend sent before named keys existed; kept so old bookmarked URLs stay valid. */
    private static final Map<String, PostCatalogSort> LEGACY_ALIASES = Map.of(
            "createdat,desc", RECENT,
            "createdat,asc", OLDEST,
            "title,asc", TITLE_ASC,
            "title,desc", TITLE_DESC
    );

    private final Sort sort;

    PostCatalogSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    /**
     * Resolves a query-string value ({@code featured}, {@code recent}, {@code oldest},
     * {@code title_asc}, {@code title_desc}; case-insensitive) to a catalog sort.
     * Legacy raw sort strings map to their equivalent; {@code null}, blank, or unknown
     * values fall back to {@link #FEATURED} instead of failing — sort keys live in
     * shareable URLs, so an outdated link must never break the page.
     */
    public static PostCatalogSort from(String key) {
        if (key == null || key.isBlank()) {
            return FEATURED;
        }

        String normalized = key.trim().toLowerCase(Locale.ROOT);

        PostCatalogSort legacy = LEGACY_ALIASES.get(normalized);
        if (legacy != null) {
            return legacy;
        }

        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FEATURED;
        }
    }
}
