package com.blog.blog_literario.utils;

import java.text.Normalizer;

/**
 * Utility for converting arbitrary text into URL-safe slugs.
 */
public class SlugUtils {

    /**
     * Converts {@code input} to a lowercase, hyphen-separated slug suitable for URLs.
     *
     * <p>The transformation pipeline:
     * <ol>
     *   <li>NFD normalization splits accented characters (e.g. CANCIÓN → C a n c i o ´ n)</li>
     *   <li>Combining diacritical marks are stripped</li>
     *   <li>Everything that is not a-z, 0-9, space, or hyphen is removed</li>
     *   <li>Whitespace runs collapse to a single hyphen</li>
     *   <li>Consecutive hyphens collapse to one</li>
     *   <li>Leading and trailing hyphens are trimmed</li>
     * </ol>
     */
    public static String toSlug(String input){
        // NFD separates diacritical marks into independent code points so they can be stripped
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                            .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");

        return normalized
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2}","-")
                .replaceAll("^-|-$","");
    }
}
