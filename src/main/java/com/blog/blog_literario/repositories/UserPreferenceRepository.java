package com.blog.blog_literario.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.UserPreferenceEntry;

public interface UserPreferenceRepository
        extends JpaRepository<UserPreferenceEntry, UserPreferenceEntry.PK> {

    List<UserPreferenceEntry> findByUserId(Integer userId);

    Optional<UserPreferenceEntry> findByUserIdAndPrefKey(Integer userId, String prefKey);
}
