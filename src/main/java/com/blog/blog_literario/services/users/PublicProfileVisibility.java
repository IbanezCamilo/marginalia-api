package com.blog.blog_literario.services.users;

/**
 * What an author has chosen to expose on public surfaces. This type is the single
 * place the redaction rule lives: every public mapper reads the author's bio and
 * photo through {@link #bioOrNull} / {@link #photoOrNull} and nothing else decides
 * what is hidden.
 *
 * <p>The author's <em>name</em> is deliberately not part of this record — it is the
 * byline on every post, so it is always public.
 */
public record PublicProfileVisibility(boolean bio, boolean photo) {

    /** Everything visible — the default posture, and the fallback for unknown authors. */
    public static final PublicProfileVisibility VISIBLE = new PublicProfileVisibility(true, true);

    /** The bio when the author shows it, otherwise {@code null}. */
    public String bioOrNull(String description) {
        return bio ? description : null;
    }

    /**
     * The stored photo filename when the author shows it, otherwise {@code null}.
     * Feeding {@code null} to {@code AvatarResolver.resolve} yields the generated
     * initials avatar, so the serialized URL stays non-null and a hidden photo is
     * indistinguishable from one that was never uploaded.
     */
    public String photoOrNull(String profilePicture) {
        return photo ? profilePicture : null;
    }
}
