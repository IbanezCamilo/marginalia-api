package com.blog.blog_literario.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.UserPreferenceEntry;

public interface UserPreferenceRepository
        extends JpaRepository<UserPreferenceEntry, UserPreferenceEntry.PK> {

    List<UserPreferenceEntry> findByUserId(Integer userId);

    Optional<UserPreferenceEntry> findByUserIdAndPrefKey(Integer userId, String prefKey);

    /**
     * Batched lookup for resolving the same set of preferences across many users in
     * one query — used by the public feed, where a per-author lookup would be N+1.
     */
    List<UserPreferenceEntry> findByUserIdInAndPrefKeyIn(
            Collection<Integer> userIds, Collection<String> prefKeys);
}
