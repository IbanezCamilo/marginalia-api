package com.blog.blog_literario.services.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.model.UserPreference;
import com.blog.blog_literario.model.UserPreferenceEntry;
import com.blog.blog_literario.repositories.UserPreferenceRepository;
import com.blog.blog_literario.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock UserPreferenceRepository preferenceRepository;
    @Mock UserRepository userRepository;

    @InjectMocks UserPreferenceService preferenceService;

    private User alice;
    private UserDetails aliceDetails;

    @BeforeEach
    void setUp() {
        alice = new User(2, "Alice", "alice@test.com", new Role("AUTHOR"));
        aliceDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice@test.com").password("x").roles("AUTHOR").build();
        lenient().when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(alice));
    }

    @Test
    void getResolved_noStoredRows_returnsAllDefaults() {
        given(preferenceRepository.findByUserId(2)).willReturn(List.of());

        Map<String, String> resolved = preferenceService.getResolved(aliceDetails);

        assertThat(resolved).containsEntry("notifications.post-moderation", "true");
        assertThat(resolved).hasSize(UserPreference.values().length);
    }

    /**
     * Pinned deliberately to the literal "true" rather than to
     * {@code pref.getDefaultValue()}: the privacy toggles must stay opt-out, and an
     * assertion phrased against the declared default would keep passing if someone
     * flipped the declaration itself.
     */
    @Test
    void getResolved_noStoredRows_privacyPreferencesDefaultToVisible() {
        given(preferenceRepository.findByUserId(2)).willReturn(List.of());

        Map<String, String> resolved = preferenceService.getResolved(aliceDetails);

        assertThat(resolved).containsEntry("privacy.show-bio", "true");
        assertThat(resolved).containsEntry("privacy.show-photo", "true");
    }

    @Test
    void resolveBooleans_noStoredRows_resolvesEveryRequestedPrefToItsDefault() {
        given(preferenceRepository.findByUserIdInAndPrefKeyIn(any(), any())).willReturn(List.of());

        Map<Integer, Map<UserPreference, Boolean>> resolved = preferenceService.resolveBooleans(
                List.of(2, 3), Set.of(UserPreference.SHOW_BIO, UserPreference.SHOW_PHOTO));

        assertThat(resolved).containsOnlyKeys(2, 3);
        assertThat(resolved.get(2)).containsEntry(UserPreference.SHOW_BIO, true)
                .containsEntry(UserPreference.SHOW_PHOTO, true);
        assertThat(resolved.get(3)).containsEntry(UserPreference.SHOW_BIO, true)
                .containsEntry(UserPreference.SHOW_PHOTO, true);
    }

    @Test
    void resolveBooleans_storedRow_overridesDefaultForThatUserOnly() {
        given(preferenceRepository.findByUserIdInAndPrefKeyIn(any(), any())).willReturn(List.of(
                new UserPreferenceEntry(2, "privacy.show-bio", "false")));

        Map<Integer, Map<UserPreference, Boolean>> resolved = preferenceService.resolveBooleans(
                List.of(2, 3), Set.of(UserPreference.SHOW_BIO, UserPreference.SHOW_PHOTO));

        assertThat(resolved.get(2)).containsEntry(UserPreference.SHOW_BIO, false)
                .containsEntry(UserPreference.SHOW_PHOTO, true);
        assertThat(resolved.get(3)).containsEntry(UserPreference.SHOW_BIO, true);
    }

    @Test
    void resolveBooleans_manyUsers_issuesASingleBatchedQuery() {
        given(preferenceRepository.findByUserIdInAndPrefKeyIn(any(), any())).willReturn(List.of());

        preferenceService.resolveBooleans(
                List.of(2, 3, 4), Set.of(UserPreference.SHOW_BIO, UserPreference.SHOW_PHOTO));

        verify(preferenceRepository).findByUserIdInAndPrefKeyIn(
                List.of(2, 3, 4), List.of("privacy.show-bio", "privacy.show-photo"));
        verify(preferenceRepository, never()).findByUserIdAndPrefKey(any(), any());
    }

    @Test
    void resolveBooleans_emptyInput_returnsEmptyMapWithoutQuerying() {
        assertThat(preferenceService.resolveBooleans(List.of(), Set.of(UserPreference.SHOW_BIO)))
                .isEmpty();
        assertThat(preferenceService.resolveBooleans(List.of(2), Set.of())).isEmpty();

        verify(preferenceRepository, never()).findByUserIdInAndPrefKeyIn(any(), any());
    }

    @Test
    void getResolved_storedRow_overridesDefault() {
        given(preferenceRepository.findByUserId(2)).willReturn(List.of(
                new UserPreferenceEntry(2, "notifications.post-moderation", "false")));

        Map<String, String> resolved = preferenceService.getResolved(aliceDetails);

        assertThat(resolved).containsEntry("notifications.post-moderation", "false");
    }

    @Test
    void update_validChange_upsertsAndReturnsResolvedMap() {
        given(preferenceRepository.findByUserId(2)).willReturn(List.of(
                new UserPreferenceEntry(2, "notifications.post-moderation", "false")));

        Map<String, String> resolved = preferenceService.update(
                aliceDetails, Map.of("notifications.post-moderation", "false"));

        verify(preferenceRepository).save(
                new UserPreferenceEntry(2, "notifications.post-moderation", "false"));
        assertThat(resolved).containsEntry("notifications.post-moderation", "false");
    }

    @Test
    void update_unknownKey_throwsAndSavesNothing() {
        assertThatThrownBy(() -> preferenceService.update(
                aliceDetails, Map.of("no.such.pref", "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no.such.pref");

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void update_invalidValue_throwsAndSavesNothing() {
        assertThatThrownBy(() -> preferenceService.update(
                aliceDetails, Map.of("notifications.post-moderation", "banana")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("banana");

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void isEnabled_noRow_usesDefaultTrue() {
        given(preferenceRepository.findByUserIdAndPrefKey(2, "notifications.post-moderation"))
                .willReturn(Optional.empty());

        assertThat(preferenceService.isEnabled(2, UserPreference.POST_MODERATION_EMAILS)).isTrue();
    }

    @Test
    void isEnabled_storedFalse_returnsFalse() {
        given(preferenceRepository.findByUserIdAndPrefKey(2, "notifications.post-moderation"))
                .willReturn(Optional.of(
                        new UserPreferenceEntry(2, "notifications.post-moderation", "false")));

        assertThat(preferenceService.isEnabled(2, UserPreference.POST_MODERATION_EMAILS)).isFalse();
    }
}
