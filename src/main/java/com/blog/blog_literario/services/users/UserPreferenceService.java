package com.blog.blog_literario.services.users;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.model.UserPreference;
import com.blog.blog_literario.model.UserPreferenceEntry;
import com.blog.blog_literario.repositories.UserPreferenceRepository;
import com.blog.blog_literario.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resolves and updates per-user preferences. The {@link UserPreference} registry
 * defines every known key with its type and default; the table stores only
 * deviations, so resolution is "stored row if present, else default". This class
 * is the single place raw stored values are read or written.
 */
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    /** Returns every registry key mapped to its stored or default value. */
    @Transactional(readOnly = true)
    public Map<String, String> getResolved(UserDetails userDetails) {
        return resolveAll(findByEmail(userDetails.getUsername()).getId());
    }

    /**
     * Applies a partial map of preference changes after validating every entry
     * against the registry, then returns the fully resolved map.
     *
     * @throws IllegalArgumentException if any key is unknown or any value is
     *                                  invalid for its declared type (nothing is saved)
     */
    @Transactional
    public Map<String, String> update(UserDetails userDetails, Map<String, String> changes) {
        User user = findByEmail(userDetails.getUsername());

        // Validate everything before writing anything, so a bad entry can't
        // leave a partial update behind.
        for (Map.Entry<String, String> change : changes.entrySet()) {
            UserPreference pref = UserPreference.fromKey(change.getKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Preferencia desconocida: '" + change.getKey() + "'"));
            if (!pref.isValidValue(change.getValue())) {
                throw new IllegalArgumentException(
                        "Valor inválido '" + change.getValue()
                        + "' para la preferencia '" + change.getKey() + "'");
            }
        }

        for (Map.Entry<String, String> change : changes.entrySet()) {
            preferenceRepository.save(new UserPreferenceEntry(
                    user.getId(), change.getKey(), change.getValue()));
        }

        return resolveAll(user.getId());
    }

    /**
     * In-transaction check for BOOLEAN preferences (e.g. deciding whether to
     * publish a notification event before the transaction commits).
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(Integer userId, UserPreference pref) {
        String value = preferenceRepository.findByUserIdAndPrefKey(userId, pref.getKey())
                .map(UserPreferenceEntry::getValue)
                .orElse(pref.getDefaultValue());
        return Boolean.parseBoolean(value);
    }

    /**
     * Batched resolution of BOOLEAN preferences for many users at once, in a single
     * query. Every requested user id is present in the returned map, and every
     * requested preference is present in each inner map (falling back to the
     * registry default when no row is stored), so callers never null-check.
     *
     * @return {@code userId -> (preference -> resolved boolean)}; empty if either argument is empty
     */
    @Transactional(readOnly = true)
    public Map<Integer, Map<UserPreference, Boolean>> resolveBooleans(
            Collection<Integer> userIds, Set<UserPreference> prefs) {

        if (userIds.isEmpty() || prefs.isEmpty()) {
            return Map.of();
        }

        // EnumSet gives declaration order, so the emitted query is deterministic.
        Set<UserPreference> orderedPrefs = EnumSet.copyOf(prefs);
        List<String> keys = orderedPrefs.stream().map(UserPreference::getKey).toList();
        Map<String, String> storedByCompositeKey = new HashMap<>();
        for (UserPreferenceEntry entry : preferenceRepository.findByUserIdInAndPrefKeyIn(userIds, keys)) {
            storedByCompositeKey.put(storedKey(entry.getUserId(), entry.getPrefKey()), entry.getValue());
        }

        Map<Integer, Map<UserPreference, Boolean>> resolved = new LinkedHashMap<>();
        for (Integer userId : userIds) {
            Map<UserPreference, Boolean> forUser = new EnumMap<>(UserPreference.class);
            for (UserPreference pref : orderedPrefs) {
                String value = storedByCompositeKey.getOrDefault(
                        storedKey(userId, pref.getKey()), pref.getDefaultValue());
                forUser.put(pref, Boolean.parseBoolean(value));
            }
            resolved.put(userId, forUser);
        }
        return resolved;
    }

    /** Composite lookup key; the id is numeric, so the first colon always delimits it. */
    private static String storedKey(Integer userId, String prefKey) {
        return userId + ":" + prefKey;
    }

    private Map<String, String> resolveAll(Integer userId) {
        Map<String, String> stored = new HashMap<>();
        for (UserPreferenceEntry entry : preferenceRepository.findByUserId(userId)) {
            stored.put(entry.getPrefKey(), entry.getValue());
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (UserPreference pref : UserPreference.values()) {
            resolved.put(pref.getKey(), stored.getOrDefault(pref.getKey(), pref.getDefaultValue()));
        }
        return resolved;
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + email));
    }
}
