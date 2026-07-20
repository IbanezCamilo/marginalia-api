package com.blog.blog_literario.services.users;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
