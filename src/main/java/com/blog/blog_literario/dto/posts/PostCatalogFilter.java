package com.blog.blog_literario.dto.posts;

/**
 * All active catalog facets for one request, already normalized. Adding a facet means
 * adding a component here + its specification — the service signature never changes again.
 */
public record PostCatalogFilter(
        String categorySlug,
        Integer categoryId,
        Integer authorId,
        ReadingTimeBucket time,
        String q) {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MIN_QUERY_LENGTH = 2;

    /**
     * Builds a filter from raw request params: lenient time parsing, q trimmed/bounded,
     * authorId parsed leniently (non-numeric/blank/null becomes null instead of erroring).
     */
    public static PostCatalogFilter of(
            String category, Integer categoryId, String authorId, String time, String q) {
        return new PostCatalogFilter(
                blankToNull(category), categoryId, parseIntegerOrNull(authorId),
                ReadingTimeBucket.from(time), normalizeQuery(q));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Integer parseIntegerOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeQuery(String q) {
        if (q == null) {
            return null;
        }
        String trimmed = q.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return null;
        }
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }
}
