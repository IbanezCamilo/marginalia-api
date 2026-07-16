package com.blog.blog_literario.dto.posts;

import java.util.Locale;

import com.blog.blog_literario.utils.ReadingTime;

/**
 * Reading-time facet buckets for the public catalog ({@code GET /api/public/posts?time=...}).
 *
 * <p>Buckets are ranges over the persisted {@code word_count} column; the bounds are
 * computed from {@link ReadingTime}'s single words-per-minute constant so the "minutes"
 * shown to readers and the ranges used for filtering can never diverge.
 * Editorial framing: short = "Un café" (≤4 min), medium = "Una pausa" (5–15 min),
 * long = "Sobremesa" (≥16 min).
 */
public enum ReadingTimeBucket {
    SHORT(0, ReadingTime.maxWordsFor(4)),
    MEDIUM(ReadingTime.maxWordsFor(4) + 1, ReadingTime.maxWordsFor(15)),
    LONG(ReadingTime.maxWordsFor(15) + 1, null);

    private final int minWords;
    private final Integer maxWords;

    ReadingTimeBucket(int minWords, Integer maxWords) {
        this.minWords = minWords;
        this.maxWords = maxWords;
    }

    public int minWords() {
        return minWords;
    }

    /** Upper bound in words, or {@code null} for the unbounded LONG bucket. */
    public Integer maxWords() {
        return maxWords;
    }

    /**
     * Resolves a query-string value (case-insensitive) to a bucket. {@code null}, blank,
     * or unknown values return {@code null} — meaning "no reading-time filter" — instead
     * of failing, because these keys live in shareable URLs.
     */
    public static ReadingTimeBucket from(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
