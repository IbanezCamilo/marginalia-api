package com.blog.blog_literario.utils;

/**
 * Single source of truth for the words→minutes reading-time interpretation.
 *
 * <p>{@code word_count} is the persisted fact on {@link com.blog.blog_literario.model.Post};
 * minutes are derived here (÷{@value #WORDS_PER_MINUTE}, rounded up, floor 1 — the same
 * formula the frontend used before this moved server-side). Both the public DTO mapping
 * and the {@link com.blog.blog_literario.dto.posts.ReadingTimeBucket} range bounds MUST
 * go through this class so the constant can change without touching stored data.
 */
public final class ReadingTime {

    public static final int WORDS_PER_MINUTE = 200;

    private ReadingTime() {
    }

    /** Minutes of reading for a word count; null or non-positive counts as the 1-minute floor. */
    public static int minutesFor(Integer wordCount) {
        if (wordCount == null || wordCount <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil((double) wordCount / WORDS_PER_MINUTE));
    }

    /** Largest word count that still reads in {@code minutes} minutes; used for bucket bounds. */
    public static int maxWordsFor(int minutes) {
        return minutes * WORDS_PER_MINUTE;
    }
}
