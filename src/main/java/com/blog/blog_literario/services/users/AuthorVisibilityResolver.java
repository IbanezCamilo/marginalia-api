package com.blog.blog_literario.services.users;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.blog.blog_literario.model.UserPreference;

import lombok.RequiredArgsConstructor;

/**
 * Turns the two privacy preferences into a {@link PublicProfileVisibility} for one
 * author or for a batch of them. Public services depend on this instead of reading
 * preferences directly, so the mapping from registry keys to visibility lives in
 * one place.
 */
@Component
@RequiredArgsConstructor
public class AuthorVisibilityResolver {

    private static final Set<UserPreference> PRIVACY_PREFS =
            Set.of(UserPreference.SHOW_BIO, UserPreference.SHOW_PHOTO);

    private final UserPreferenceService userPreferenceService;

    public PublicProfileVisibility forAuthor(Integer authorId) {
        return forAuthors(Set.of(authorId)).getOrDefault(authorId, PublicProfileVisibility.VISIBLE);
    }

    /**
     * One query for the whole batch — the public feed resolves every distinct author
     * of a page in a single call rather than one lookup per post.
     *
     * @return an entry for every requested id; ids resolve to defaults when nothing is stored
     */
    public Map<Integer, PublicProfileVisibility> forAuthors(Collection<Integer> authorIds) {
        Map<Integer, Map<UserPreference, Boolean>> resolved =
                userPreferenceService.resolveBooleans(authorIds, PRIVACY_PREFS);

        Map<Integer, PublicProfileVisibility> visibility = new LinkedHashMap<>();
        resolved.forEach((authorId, prefs) -> visibility.put(authorId, new PublicProfileVisibility(
                prefs.get(UserPreference.SHOW_BIO),
                prefs.get(UserPreference.SHOW_PHOTO))));
        return visibility;
    }
}
