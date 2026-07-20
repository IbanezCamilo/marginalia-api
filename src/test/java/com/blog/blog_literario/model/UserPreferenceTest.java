package com.blog.blog_literario.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserPreferenceTest {

    @Test
    void fromKey_knownKey_returnsEntry() {
        assertThat(UserPreference.fromKey("notifications.post-moderation"))
                .contains(UserPreference.POST_MODERATION_EMAILS);
    }

    @Test
    void fromKey_unknownKey_returnsEmpty() {
        assertThat(UserPreference.fromKey("nope")).isEmpty();
        assertThat(UserPreference.fromKey(null)).isEmpty();
    }

    @Test
    void postModerationEmails_defaultsToTrue() {
        assertThat(UserPreference.POST_MODERATION_EMAILS.getDefaultValue()).isEqualTo("true");
        assertThat(UserPreference.POST_MODERATION_EMAILS.getKey())
                .isEqualTo("notifications.post-moderation");
    }

    @Test
    void isValidValue_booleanPref_acceptsOnlyTrueOrFalse() {
        UserPreference pref = UserPreference.POST_MODERATION_EMAILS;
        assertThat(pref.isValidValue("true")).isTrue();
        assertThat(pref.isValidValue("false")).isTrue();
        assertThat(pref.isValidValue("TRUE")).isFalse();
        assertThat(pref.isValidValue("yes")).isFalse();
        assertThat(pref.isValidValue("")).isFalse();
        assertThat(pref.isValidValue(null)).isFalse();
    }
}
