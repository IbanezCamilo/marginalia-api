package com.blog.blog_literario.model;

import java.util.Optional;

/**
 * Registry of every known user preference: wire key, expected value type, and
 * default. This enum is the single source of truth — the {@code user_preferences}
 * table stores only deviations from these defaults, and nothing outside
 * {@code UserPreferenceService} parses raw stored values. Adding a preference is
 * one new entry here (plus its UI control); no migration needed.
 */
public enum UserPreference {

    /** Email the author when moderation changes one of their posts' status. */
    POST_MODERATION_EMAILS("notifications.post-moderation", ValueType.BOOLEAN, "true"),

    /** Show the author's bio on their public profile and alongside their posts. */
    SHOW_BIO("privacy.show-bio", ValueType.BOOLEAN, "true"),

    /**
     * Show the author's uploaded photo publicly. When off, the generated initials
     * avatar is served instead — the same one an author who never uploaded a photo gets.
     */
    SHOW_PHOTO("privacy.show-photo", ValueType.BOOLEAN, "true");

    /** Value types a preference can declare; validation lives with the type. */
    public enum ValueType {
        BOOLEAN;

        boolean isValid(String value) {
            return "true".equals(value) || "false".equals(value);
        }
    }

    private final String key;
    private final ValueType type;
    private final String defaultValue;

    UserPreference(String key, ValueType type, String defaultValue) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /** Looks up a registry entry by its wire key (e.g. {@code notifications.post-moderation}). */
    public static Optional<UserPreference> fromKey(String key) {
        for (UserPreference pref : values()) {
            if (pref.key.equals(key)) {
                return Optional.of(pref);
            }
        }
        return Optional.empty();
    }

    /** True when {@code value} is a valid serialized value for this preference's type. */
    public boolean isValidValue(String value) {
        return value != null && type.isValid(value);
    }
}
