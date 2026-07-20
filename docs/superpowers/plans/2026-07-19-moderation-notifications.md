# Moderation Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Email authors when moderation changes one of their posts' status, controlled by a new notification-preference stored per user and surfaced in a new `/user/settings` page.

**Architecture:** A generic key-value `user_preferences` table stores only deviations from defaults; a `UserPreference` registry enum is the single source of truth for keys, types, and defaults. Moderation services publish a `PostModerationStatusChanged` domain event in-transaction (skipped when the pref is off, the actor is the author, or the status didn't change); an `@Async AFTER_COMMIT` listener sends the email — the exact pattern of the existing `AuthorRequestSubmitted` flow.

**Tech Stack:** Spring Boot 3 (Flyway, JPA/H2-in-tests, Resend SDK), React 19 + Vite (shadcn/ui, Tailwind 4, Vitest + Testing Library).

**Spec:** `docs/superpowers/specs/2026-07-19-moderation-notifications-design.md` (marginalia-api repo).

## Global Constraints

- Two repos: **marginalia-api** (`C:\repos\BlogProyecto\marginalia-api`, Tasks 1–9, branch `feature/moderation-notifications` already exists) and **marginalia-web** (`C:\repos\BlogProyecto\marginalia-web`, Tasks 10–14, create branch `feature/moderation-notifications` from `main` in Task 10).
- marginalia-web has pre-existing uncommitted changes (`.gitignore`, `marginalia-guia-de-voz.md`) — NEVER stage them; always `git add` explicit paths.
- Shell is Windows PowerShell 5.1: no `&&` chaining; backend tests run with `.\mvnw.cmd`.
- All user-facing copy (UI text, email bodies, exception messages) is Spanish; code identifiers and comments are English.
- The preference table column is `pref_value`, NOT `value` — `VALUE` is a reserved keyword in H2, and tests generate the schema on H2 via `ddl-auto=create-drop`.
- Never edit existing Flyway migrations; only add `V11__add_user_preferences.sql`.
- Email delivery failures must never propagate into moderation transactions.
- Frontend layering: pages import hooks only; hooks call services; services call `apiClient`. Path alias `@` → `src/`.
- Do not run `git push` in either repo.

---

### Task 1: Flyway migration V11 (marginalia-api)

**Files:**
- Create: `src/main/resources/db/migration/V11__add_user_preferences.sql`

**Interfaces:**
- Produces: table `user_preferences(user_id, pref_key, pref_value)` that Task 3's entity maps.

- [ ] **Step 1: Create the migration**

```sql
-- Per-user preference overrides. Only deviations from defaults are stored:
-- a missing row means "use the default declared in the UserPreference enum".
-- pref_value is deliberately VARCHAR, not BOOLEAN: type-agnostic for future
-- prefs (cadence, font size...) and named pref_value because VALUE is a
-- reserved keyword in H2 (the test database).
CREATE TABLE user_preferences (
    user_id    INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pref_key   VARCHAR(50) NOT NULL,
    pref_value VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, pref_key)
);
```

- [ ] **Step 2: Run the Flyway consistency test**

Run: `.\mvnw.cmd test "-Dtest=FlywayMigrationConsistencyTest"`
Expected: BUILD SUCCESS (the test validates migration naming/ordering; V11 follows V10).

- [ ] **Step 3: Commit**

```powershell
git add src/main/resources/db/migration/V11__add_user_preferences.sql
git commit -m "feat: add user_preferences table (V11)"
```

---

### Task 2: UserPreference registry enum (marginalia-api)

**Files:**
- Create: `src/main/java/com/blog/blog_literario/model/UserPreference.java`
- Test: `src/test/java/com/blog/blog_literario/model/UserPreferenceTest.java`

**Interfaces:**
- Produces: `UserPreference.POST_MODERATION_EMAILS`, `String getKey()`, `String getDefaultValue()`, `static Optional<UserPreference> fromKey(String)`, `boolean isValidValue(String)`. Key string: `"notifications.post-moderation"`, default `"true"`.

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=UserPreferenceTest"`
Expected: COMPILATION ERROR — `UserPreference` does not exist.

- [ ] **Step 3: Write the implementation**

```java
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
    POST_MODERATION_EMAILS("notifications.post-moderation", ValueType.BOOLEAN, "true");

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=UserPreferenceTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/blog/blog_literario/model/UserPreference.java src/test/java/com/blog/blog_literario/model/UserPreferenceTest.java
git commit -m "feat: add UserPreference registry enum"
```

---

### Task 3: UserPreferenceEntry entity + repository (marginalia-api)

**Files:**
- Create: `src/main/java/com/blog/blog_literario/model/UserPreferenceEntry.java`
- Create: `src/main/java/com/blog/blog_literario/repositories/UserPreferenceRepository.java`
- Test: `src/test/java/com/blog/blog_literario/repositories/UserPreferenceRepositoryTest.java`

**Interfaces:**
- Consumes: table from Task 1 (in dev/prod; tests generate schema from the entity via H2 `create-drop`).
- Produces: `UserPreferenceEntry(Integer userId, String prefKey, String value)` with getters/setters via Lombok `@Data`; static PK class `UserPreferenceEntry.PK(Integer userId, String prefKey)`; repository methods `List<UserPreferenceEntry> findByUserId(Integer)` and `Optional<UserPreferenceEntry> findByUserIdAndPrefKey(Integer, String)`.

- [ ] **Step 1: Write the failing repository test**

```java
package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.model.UserPreferenceEntry;

@DataJpaTest
@ActiveProfiles("test")
class UserPreferenceRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired UserPreferenceRepository preferenceRepository;

    private User alice;

    @BeforeEach
    void setUp() {
        Role reader = em.persist(new Role("READER"));
        User user = new User();
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPassword("encoded");
        user.setRole(reader);
        alice = em.persist(user);
    }

    @Test
    void saveAndFindByUserIdAndPrefKey_roundTrips() {
        preferenceRepository.save(new UserPreferenceEntry(
                alice.getId(), "notifications.post-moderation", "false"));
        em.flush();
        em.clear();

        Optional<UserPreferenceEntry> found = preferenceRepository
                .findByUserIdAndPrefKey(alice.getId(), "notifications.post-moderation");

        assertThat(found).isPresent();
        assertThat(found.get().getValue()).isEqualTo("false");
    }

    @Test
    void save_sameUserAndKey_updatesInsteadOfDuplicating() {
        preferenceRepository.save(new UserPreferenceEntry(
                alice.getId(), "notifications.post-moderation", "false"));
        em.flush();
        preferenceRepository.save(new UserPreferenceEntry(
                alice.getId(), "notifications.post-moderation", "true"));
        em.flush();
        em.clear();

        List<UserPreferenceEntry> all = preferenceRepository.findByUserId(alice.getId());

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getValue()).isEqualTo("true");
    }

    @Test
    void findByUserId_noRows_returnsEmptyList() {
        assertThat(preferenceRepository.findByUserId(alice.getId())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=UserPreferenceRepositoryTest"`
Expected: COMPILATION ERROR — entity and repository do not exist.

- [ ] **Step 3: Write entity and repository**

`src/main/java/com/blog/blog_literario/model/UserPreferenceEntry.java`:

```java
package com.blog.blog_literario.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One stored user-preference override. Rows exist only for values that deviate
 * from the defaults declared in {@link UserPreference}; resolution happens in
 * {@code UserPreferenceService}. The column is {@code pref_value} because
 * {@code VALUE} is a reserved keyword in H2 (the test database).
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserPreferenceEntry.PK.class)
@Table(name = "user_preferences")
public class UserPreferenceEntry {

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Id
    @Column(name = "pref_key", length = 50)
    private String prefKey;

    @Column(name = "pref_value", nullable = false, length = 50)
    private String value;

    /** Composite key (user_id, pref_key); equals/hashCode via Lombok. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private Integer userId;
        private String prefKey;
    }
}
```

`src/main/java/com/blog/blog_literario/repositories/UserPreferenceRepository.java`:

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=UserPreferenceRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/blog/blog_literario/model/UserPreferenceEntry.java src/main/java/com/blog/blog_literario/repositories/UserPreferenceRepository.java src/test/java/com/blog/blog_literario/repositories/UserPreferenceRepositoryTest.java
git commit -m "feat: add UserPreferenceEntry entity and repository"
```

---

### Task 4: UserPreferenceService (marginalia-api)

**Files:**
- Create: `src/main/java/com/blog/blog_literario/services/users/UserPreferenceService.java`
- Test: `src/test/java/com/blog/blog_literario/services/users/UserPreferenceServiceTest.java`

**Interfaces:**
- Consumes: `UserPreferenceRepository` (Task 3), `UserPreference` (Task 2), `UserRepository.findByEmail(String)`.
- Produces: `Map<String,String> getResolved(UserDetails)`, `Map<String,String> update(UserDetails, Map<String,String>)`, `boolean isEnabled(Integer userId, UserPreference pref)`. Throws `IllegalArgumentException` (→ 400 via `GlobalExceptionHandler`) for unknown keys or type-invalid values.

- [ ] **Step 1: Write the failing test**

```java
package com.blog.blog_literario.services.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(alice));
    }

    @Test
    void getResolved_noStoredRows_returnsAllDefaults() {
        given(preferenceRepository.findByUserId(2)).willReturn(List.of());

        Map<String, String> resolved = preferenceService.getResolved(aliceDetails);

        assertThat(resolved).containsEntry("notifications.post-moderation", "true");
        assertThat(resolved).hasSize(UserPreference.values().length);
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=UserPreferenceServiceTest"`
Expected: COMPILATION ERROR — service does not exist.

- [ ] **Step 3: Write the implementation**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=UserPreferenceServiceTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/blog/blog_literario/services/users/UserPreferenceService.java src/test/java/com/blog/blog_literario/services/users/UserPreferenceServiceTest.java
git commit -m "feat: add UserPreferenceService with registry-validated resolution"
```

---

### Task 5: MyPreferencesController + SecurityConfig matcher (marginalia-api)

**Files:**
- Create: `src/main/java/com/blog/blog_literario/controllers/users/MyPreferencesController.java`
- Modify: `src/main/java/com/blog/blog_literario/config/SecurityConfig.java` (after the `/api/me/profile/**` matcher, ~line 90)
- Test: `src/test/java/com/blog/blog_literario/controllers/users/MyPreferencesControllerTest.java`

**Interfaces:**
- Consumes: `UserPreferenceService.getResolved(UserDetails)` / `.update(UserDetails, Map)` (Task 4).
- Produces: `GET /api/me/preferences` → 200 with resolved map; `PUT /api/me/preferences` (JSON object body) → 200 with resolved map, 400 on invalid key/value.

- [ ] **Step 1: Write the failing test**

```java
package com.blog.blog_literario.controllers.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.users.UserPreferenceService;
import com.blog.blog_literario.support.WebMvcTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MyPreferencesController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class MyPreferencesControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserPreferenceService userPreferenceService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void getPreferences_authenticated_returnsResolvedMap() throws Exception {
        given(userPreferenceService.getResolved(any()))
                .willReturn(Map.of("notifications.post-moderation", "true"));

        mockMvc.perform(get("/api/me/preferences")
                .with(user("alice@test.com").roles("AUTHOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['notifications.post-moderation']").value("true"));
    }

    @Test
    void getPreferences_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/me/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePreferences_validBody_returnsResolvedMap() throws Exception {
        given(userPreferenceService.update(any(), any()))
                .willReturn(Map.of("notifications.post-moderation", "false"));

        mockMvc.perform(put("/api/me/preferences")
                .with(user("alice@test.com").roles("AUTHOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notifications.post-moderation\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['notifications.post-moderation']").value("false"));
    }

    @Test
    void updatePreferences_unknownKey_returns400() throws Exception {
        given(userPreferenceService.update(any(), any()))
                .willThrow(new IllegalArgumentException("Preferencia desconocida: 'no.such.pref'"));

        mockMvc.perform(put("/api/me/preferences")
                .with(user("alice@test.com").roles("AUTHOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"no.such.pref\":\"true\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=MyPreferencesControllerTest"`
Expected: COMPILATION ERROR — controller does not exist.

- [ ] **Step 3: Write the controller and the SecurityConfig matcher**

`MyPreferencesController.java`:

```java
package com.blog.blog_literario.controllers.users;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.services.users.UserPreferenceService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints for an authenticated user to read and update their own preferences.
 * Responses are always the fully resolved map (defaults merged in), so the
 * frontend never computes defaults.
 */
@Tag(name = "My Preferences")
@SecurityRequirement(name = "cookieAuth")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/me/preferences")
public class MyPreferencesController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public ResponseEntity<Map<String, String>> getPreferences(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userPreferenceService.getResolved(userDetails));
    }

    /** Accepts a partial map of {@code key -> value}; unknown keys or invalid values → 400. */
    @PutMapping
    public ResponseEntity<Map<String, String>> updatePreferences(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> changes) {
        return ResponseEntity.ok(userPreferenceService.update(userDetails, changes));
    }
}
```

In `SecurityConfig.java`, directly below `.requestMatchers("/api/me/profile/**").authenticated()`:

```java
                .requestMatchers("/api/me/preferences").authenticated()
```

(`anyRequest().authenticated()` already covers it; the explicit matcher keeps the `/api/me/*` block self-documenting.)

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=MyPreferencesControllerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/blog/blog_literario/controllers/users/MyPreferencesController.java src/main/java/com/blog/blog_literario/config/SecurityConfig.java src/test/java/com/blog/blog_literario/controllers/users/MyPreferencesControllerTest.java
git commit -m "feat: add GET/PUT /api/me/preferences endpoints"
```

---

### Task 6: PostModerationStatusChanged event + PostModerationEventPublisher (marginalia-api)

**Files:**
- Create: `src/main/java/com/blog/blog_literario/events/PostModerationStatusChanged.java`
- Create: `src/main/java/com/blog/blog_literario/services/moderator/PostModerationEventPublisher.java`
- Test: `src/test/java/com/blog/blog_literario/services/moderator/PostModerationEventPublisherTest.java`

**Interfaces:**
- Consumes: `UserPreferenceService.isEnabled(Integer, UserPreference)` (Task 4), `Post` (getters: `getId()`, `getTitle()`, `getStatus()`, `getModerationNote()`, `getAuthor()`), Spring's `ApplicationEventPublisher`.
- Produces: record `PostModerationStatusChanged(Integer postId, String postTitle, String authorName, String authorEmail, PostStatus previousStatus, PostStatus newStatus, String moderationNote, String idempotencyKey)`; component method `void publishStatusChange(Post post, Integer actorId, PostStatus previousStatus)`.

- [ ] **Step 1: Write the failing test**

```java
package com.blog.blog_literario.services.moderator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.blog.blog_literario.events.PostModerationStatusChanged;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.model.UserPreference;
import com.blog.blog_literario.services.users.UserPreferenceService;

@ExtendWith(MockitoExtension.class)
class PostModerationEventPublisherTest {

    @Mock ApplicationEventPublisher eventPublisher;
    @Mock UserPreferenceService userPreferenceService;

    @InjectMocks PostModerationEventPublisher publisher;

    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        author = new User(2, "Alice", "alice@test.com", new Role("AUTHOR"));
        post = new Post("Mi cuento", "Contenido", PostStatus.PUBLISHED, "mi-cuento", author, null);
        post.setId(10);
    }

    @Test
    void publishStatusChange_statusChanged_publishesEventWithFields() {
        given(userPreferenceService.isEnabled(2, UserPreference.POST_MODERATION_EMAILS))
                .willReturn(true);

        publisher.publishStatusChange(post, 1, PostStatus.DRAFT);

        ArgumentCaptor<PostModerationStatusChanged> captor =
                ArgumentCaptor.forClass(PostModerationStatusChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PostModerationStatusChanged event = captor.getValue();
        assertThat(event.postId()).isEqualTo(10);
        assertThat(event.postTitle()).isEqualTo("Mi cuento");
        assertThat(event.authorName()).isEqualTo("Alice");
        assertThat(event.authorEmail()).isEqualTo("alice@test.com");
        assertThat(event.previousStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(event.newStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(event.idempotencyKey()).startsWith("post-moderation/10/");
    }

    @Test
    void publishStatusChange_untitledDraft_usesFallbackTitle() {
        Post untitled = new Post(null, "Contenido", PostStatus.REJECTED, null, author, null);
        untitled.setId(11);
        given(userPreferenceService.isEnabled(2, UserPreference.POST_MODERATION_EMAILS))
                .willReturn(true);

        publisher.publishStatusChange(untitled, 1, PostStatus.DRAFT);

        ArgumentCaptor<PostModerationStatusChanged> captor =
                ArgumentCaptor.forClass(PostModerationStatusChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().postTitle()).isEqualTo("(sin título)");
    }

    @Test
    void publishStatusChange_sameStatus_publishesNothing() {
        publisher.publishStatusChange(post, 1, PostStatus.PUBLISHED);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishStatusChange_actorIsAuthor_publishesNothing() {
        publisher.publishStatusChange(post, 2, PostStatus.DRAFT);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishStatusChange_preferenceDisabled_publishesNothing() {
        given(userPreferenceService.isEnabled(2, UserPreference.POST_MODERATION_EMAILS))
                .willReturn(false);

        publisher.publishStatusChange(post, 1, PostStatus.DRAFT);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=PostModerationEventPublisherTest"`
Expected: COMPILATION ERROR — event and publisher do not exist.

- [ ] **Step 3: Write event and publisher**

`src/main/java/com/blog/blog_literario/events/PostModerationStatusChanged.java`:

```java
package com.blog.blog_literario.events;

import com.blog.blog_literario.model.PostStatus;

/**
 * Domain event published when moderation changes a post's status. Published
 * inside the moderation transaction (only when the author should be notified —
 * see {@code PostModerationEventPublisher}) and consumed by an
 * {@code AFTER_COMMIT} listener that emails the author, so the notification can
 * never reference a change that was rolled back.
 *
 * <p>All recipient data rides in the event because the listener runs
 * asynchronously after commit, outside any transaction, and must not touch
 * repositories.
 *
 * @param postId         ID of the moderated post
 * @param postTitle      post title, never null ({@code "(sin título)"} for untitled drafts)
 * @param authorName     author's display name
 * @param authorEmail    author's email address
 * @param previousStatus status before the moderation action
 * @param newStatus      status after the moderation action
 * @param moderationNote moderator's note for the author (may be null)
 * @param idempotencyKey provider-level deduplication key, unique per action
 *                       ({@code post-moderation/<postId>/<uuid>})
 */
public record PostModerationStatusChanged(
        Integer postId,
        String postTitle,
        String authorName,
        String authorEmail,
        PostStatus previousStatus,
        PostStatus newStatus,
        String moderationNote,
        String idempotencyKey
) {
}
```

`src/main/java/com/blog/blog_literario/services/moderator/PostModerationEventPublisher.java`:

```java
package com.blog.blog_literario.services.moderator;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.blog.blog_literario.events.PostModerationStatusChanged;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.UserPreference;
import com.blog.blog_literario.services.users.UserPreferenceService;

import lombok.RequiredArgsConstructor;

/**
 * Publishes {@link PostModerationStatusChanged} for a moderated post. Shared by
 * the moderator and admin moderation services so the skip rules live in one place:
 * no event when the status didn't actually change, when the actor is the author
 * (no self-notifications), or when the author disabled moderation emails.
 *
 * <p>Must be called inside the moderation transaction, after the post has been
 * mutated — the preference read and the skip decisions happen here so the
 * after-commit listener never touches repositories.
 */
@Component
@RequiredArgsConstructor
public class PostModerationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final UserPreferenceService userPreferenceService;

    public void publishStatusChange(Post post, Integer actorId, PostStatus previousStatus) {
        if (post.getStatus() == previousStatus) {
            return;
        }
        Integer authorId = post.getAuthor().getId();
        if (authorId.equals(actorId)) {
            return;
        }
        if (!userPreferenceService.isEnabled(authorId, UserPreference.POST_MODERATION_EMAILS)) {
            return;
        }

        String title = (post.getTitle() != null && !post.getTitle().isBlank())
                ? post.getTitle() : "(sin título)";

        eventPublisher.publishEvent(new PostModerationStatusChanged(
                post.getId(),
                title,
                post.getAuthor().getName(),
                post.getAuthor().getEmail(),
                previousStatus,
                post.getStatus(),
                post.getModerationNote(),
                "post-moderation/" + post.getId() + "/" + UUID.randomUUID()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=PostModerationEventPublisherTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/blog/blog_literario/events/PostModerationStatusChanged.java src/main/java/com/blog/blog_literario/services/moderator/PostModerationEventPublisher.java src/test/java/com/blog/blog_literario/services/moderator/PostModerationEventPublisherTest.java
git commit -m "feat: add PostModerationStatusChanged event and shared publisher"
```

---

### Task 7: Wire the publisher into both moderation services (marginalia-api)

**Files:**
- Modify: `src/main/java/com/blog/blog_literario/services/moderator/ModeratorPostService.java`
- Modify: `src/main/java/com/blog/blog_literario/services/admin/AdminPostModerationService.java`
- Modify: `src/test/java/com/blog/blog_literario/services/moderator/ModeratorPostServiceTest.java`
- Modify: `src/test/java/com/blog/blog_literario/services/admin/AdminPostModerationServiceTest.java`

**Interfaces:**
- Consumes: `PostModerationEventPublisher.publishStatusChange(Post, Integer actorId, PostStatus previousStatus)` (Task 6).
- Produces: every moderation status change (moderator updateStatus, admin updateStatus, admin resetPost) calls the publisher exactly once, with the pre-mutation status.

- [ ] **Step 1: Add failing tests to both existing test classes**

In `ModeratorPostServiceTest`, add the mock field next to the existing ones:

```java
    @Mock PostModerationEventPublisher moderationEventPublisher;
```

and add these tests:

```java
    @Test
    void updateStatus_draftToPublished_notifiesWithPreviousStatus() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("PUBLISHED", null));

        verify(moderationEventPublisher).publishStatusChange(post, 1, PostStatus.DRAFT);
    }

    @Test
    void updateStatus_rejection_notifiesWithPreviousStatus() {
        Post post = newPost(PostStatus.PUBLISHED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(moderator));

        moderatorService.updateStatus(1, 1, new ModeratorStatusUpdateRequest("REJECTED", "Corrige el título"));

        verify(moderationEventPublisher).publishStatusChange(post, 1, PostStatus.PUBLISHED);
    }

    @Test
    void updateStatus_invalidTransition_doesNotNotify() {
        Post post = newPost(PostStatus.ARCHIVED);
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> moderatorService.updateStatus(
                1, 1, new ModeratorStatusUpdateRequest("PUBLISHED", null)))
                .isInstanceOf(IllegalStateException.class);

        verify(moderationEventPublisher, never()).publishStatusChange(any(), any(), any());
    }
```

In `AdminPostModerationServiceTest`, add the same mock field:

```java
    @Mock PostModerationEventPublisher moderationEventPublisher;
```

(import `com.blog.blog_literario.services.moderator.PostModerationEventPublisher`) and add these tests. They reuse the file's existing fixtures: `author` is user id 2, `admin` is user id 1, and `newPost(status)` builds a post by `author`. `Post` is Lombok `@Data`, so `setRejectionCount(int)` exists:

```java
    @Test
    void updateStatus_statusForced_notifiesWithPreviousStatus() {
        Post post = newPost(PostStatus.DRAFT);
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(admin));

        adminService.updateStatus(1, 1, new AdminStatusUpdateRequest("PUBLISHED", "Looks good"));

        verify(moderationEventPublisher).publishStatusChange(post, 1, PostStatus.DRAFT);
    }

    @Test
    void resetPost_blockedPost_notifiesWithArchivedAsPreviousStatus() {
        Post post = newPost(PostStatus.ARCHIVED);
        post.setRejectionCount(3); // permanently blocked
        given(postRepository.findById(1)).willReturn(Optional.of(post));
        given(userRepository.findById(1)).willReturn(Optional.of(admin));

        adminService.resetPost(1, 1, "Puedes reintentarlo");

        verify(moderationEventPublisher).publishStatusChange(post, 1, PostStatus.ARCHIVED);
    }

    @Test
    void updateStatus_blockedPost_doesNotNotify() {
        Post post = newPost(PostStatus.ARCHIVED);
        post.setRejectionCount(3); // permanently blocked → updateStatus must throw
        given(postRepository.findById(1)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> adminService.updateStatus(
                1, 1, new AdminStatusUpdateRequest("PUBLISHED", null)))
                .isInstanceOf(IllegalStateException.class);

        verify(moderationEventPublisher, never()).publishStatusChange(any(), any(), any());
    }
```

- [ ] **Step 2: Run both test classes to verify the new tests fail**

Run: `.\mvnw.cmd test "-Dtest=ModeratorPostServiceTest,AdminPostModerationServiceTest"`
Expected: COMPILATION ERROR (services don't have the new dependency yet).

- [ ] **Step 3: Wire the publisher into `ModeratorPostService`**

Add the dependency field (constructor is Lombok `@RequiredArgsConstructor`):

```java
    private final PostModerationEventPublisher moderationEventPublisher;
```

In `updateStatus`, capture the previous status right after loading the post, and publish after saving:

```java
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post no encontrado con ID: " + postId));

        PostStatus previousStatus = post.getStatus();
        PostStatus newStatus = parseStatus(request.status());

        validateTransition(post.getStatus(), newStatus);

        if (newStatus == PostStatus.REJECTED) {
            handleRejection(post, moderatorId, request.moderationNote());
        } else {
            User moderator = getModerator(moderatorId);
            post.setStatus(newStatus);
            post.recordModeration(moderator, request.moderationNote());
        }

        postRepository.save(post);
        moderationEventPublisher.publishStatusChange(post, moderatorId, previousStatus);
        return toResponse(post);
```

- [ ] **Step 4: Wire the publisher into `AdminPostModerationService`**

Add the same dependency field (import `com.blog.blog_literario.services.moderator.PostModerationEventPublisher`):

```java
    private final PostModerationEventPublisher moderationEventPublisher;
```

`updateStatus` already captures `PostStatus previousStatus = post.getStatus();` — add the publish call directly after `postRepository.save(post);`:

```java
        postRepository.save(post);
        moderationEventPublisher.publishStatusChange(post, adminId, previousStatus);
```

In `resetPost`, capture the previous status before `resetForAuthor()` and publish after saving:

```java
        int rejectionCountBeforeReset = post.getRejectionCount();
        PostStatus previousStatus = post.getStatus();

        // resetForAuthor() sets status=DRAFT, rejectionCount=0, clears note and moderation metadata
        post.resetForAuthor();
        ...
        postRepository.save(post);
        moderationEventPublisher.publishStatusChange(post, adminId, previousStatus);
```

(Keep the existing `adminActionLogService.record` calls unchanged in both methods.)

- [ ] **Step 5: Run both test classes — new tests pass, existing tests still pass**

Run: `.\mvnw.cmd test "-Dtest=ModeratorPostServiceTest,AdminPostModerationServiceTest"`
Expected: PASS, including every pre-existing test. If any pre-existing test fails, STOP and report it — do not adjust its expectations.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/blog/blog_literario/services/moderator/ModeratorPostService.java src/main/java/com/blog/blog_literario/services/admin/AdminPostModerationService.java src/test/java/com/blog/blog_literario/services/moderator/ModeratorPostServiceTest.java src/test/java/com/blog/blog_literario/services/admin/AdminPostModerationServiceTest.java
git commit -m "feat: publish moderation status changes from moderator and admin services"
```

---

### Task 8: EmailService method, logging impl, and after-commit listener (marginalia-api)

**Files:**
- Modify: `src/main/java/com/blog/blog_literario/services/email/EmailService.java`
- Modify: `src/main/java/com/blog/blog_literario/services/email/LoggingEmailService.java`
- Create: `src/main/java/com/blog/blog_literario/services/email/PostModerationNotificationListener.java`
- Test: `src/test/java/com/blog/blog_literario/services/email/PostModerationNotificationListenerTest.java`

**Interfaces:**
- Consumes: `PostModerationStatusChanged` (Task 6), `FrontendProperties.url()`.
- Produces: `EmailService.sendPostModerationNotification(String to, String authorName, String postTitle, PostStatus previousStatus, PostStatus newStatus, String moderationNote, String postsUrl, String idempotencyKey)` — Task 9 implements it in `ResendEmailService`.

Note: this task will NOT compile until `ResendEmailService` also implements the new
interface method. To keep the build green within the task, Task 8 Step 3 adds a
minimal stub to `ResendEmailService` that Task 9 replaces with the real template.

- [ ] **Step 1: Write the failing listener test**

```java
package com.blog.blog_literario.services.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.PostModerationStatusChanged;
import com.blog.blog_literario.model.PostStatus;

@ExtendWith(MockitoExtension.class)
class PostModerationNotificationListenerTest {

    @Mock EmailService emailService;

    private PostModerationStatusChanged event() {
        return new PostModerationStatusChanged(
                10, "Mi cuento", "Alice", "alice@test.com",
                PostStatus.DRAFT, PostStatus.PUBLISHED, "Buen trabajo",
                "post-moderation/10/abc");
    }

    @Test
    void onPostModerationStatusChanged_buildsPostsUrlAndDelegates() {
        PostModerationNotificationListener listener = new PostModerationNotificationListener(
                emailService, new FrontendProperties("http://localhost:5173"));

        listener.onPostModerationStatusChanged(event());

        verify(emailService).sendPostModerationNotification(
                "alice@test.com", "Alice", "Mi cuento",
                PostStatus.DRAFT, PostStatus.PUBLISHED, "Buen trabajo",
                "http://localhost:5173/user/posts",
                "post-moderation/10/abc");
    }

    @Test
    void onPostModerationStatusChanged_swallowsSendFailures() {
        PostModerationNotificationListener listener = new PostModerationNotificationListener(
                emailService, new FrontendProperties("http://localhost:5173"));
        willThrow(new RuntimeException("boom")).given(emailService)
                .sendPostModerationNotification(any(), any(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> listener.onPostModerationStatusChanged(event()))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=PostModerationNotificationListenerTest"`
Expected: COMPILATION ERROR — listener and interface method do not exist.

- [ ] **Step 3: Extend the interface and both implementations, add the listener**

Add to `EmailService.java` (import `com.blog.blog_literario.model.PostStatus`):

```java
    /**
     * Notifies an author that moderation changed one of their posts' status.
     *
     * @param to             author's email address
     * @param authorName     author's display name, used in the greeting
     * @param postTitle      post title (never null; callers substitute a fallback for untitled drafts)
     * @param previousStatus status before the moderation action
     * @param newStatus      status after the moderation action (drives subject/headline copy)
     * @param moderationNote moderator's note for the author (may be null)
     * @param postsUrl       absolute frontend URL of the author's posts list
     * @param idempotencyKey provider-level deduplication key for network retries,
     *                       unique per moderation action (e.g. {@code post-moderation/<postId>/<uuid>})
     */
    void sendPostModerationNotification(String to, String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote,
            String postsUrl, String idempotencyKey);
```

Add to `LoggingEmailService.java` (import `com.blog.blog_literario.model.PostStatus`):

```java
    @Override
    public void sendPostModerationNotification(String to, String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote,
            String postsUrl, String idempotencyKey) {
        log.info("[email.provider=logging] Post moderation notification for {} <{}>: \"{}\" {} -> {} (nota: {})",
                authorName, to, postTitle, previousStatus.name(), newStatus.name(), moderationNote);
    }
```

Add a temporary stub to `ResendEmailService.java` so the project compiles (replaced in Task 9):

```java
    @Override
    public void sendPostModerationNotification(String to, String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote,
            String postsUrl, String idempotencyKey) {
        // Implemented in the next commit (template + retry wiring).
        throw new UnsupportedOperationException("Not implemented yet");
    }
```

Create `PostModerationNotificationListener.java`:

```java
package com.blog.blog_literario.services.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.PostModerationStatusChanged;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Emails the author once the moderation transaction has committed —
 * {@code AFTER_COMMIT} guarantees the notification can never reference a status
 * change that was rolled back, and {@code @Async} keeps the HTTP response from
 * waiting on the email provider.
 *
 * <p>Failures are logged, never rethrown: the moderation result stands
 * regardless of email delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostModerationNotificationListener {

    private final EmailService emailService;
    private final FrontendProperties frontendProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostModerationStatusChanged(PostModerationStatusChanged event) {
        try {
            String postsUrl = frontendProperties.url() + "/user/posts";
            emailService.sendPostModerationNotification(
                    event.authorEmail(), event.authorName(), event.postTitle(),
                    event.previousStatus(), event.newStatus(), event.moderationNote(),
                    postsUrl, event.idempotencyKey());
        } catch (Exception e) {
            log.error("Failed to send post moderation notification for post {}", event.postId(), e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=PostModerationNotificationListenerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/blog/blog_literario/services/email/EmailService.java src/main/java/com/blog/blog_literario/services/email/LoggingEmailService.java src/main/java/com/blog/blog_literario/services/email/ResendEmailService.java src/main/java/com/blog/blog_literario/services/email/PostModerationNotificationListener.java src/test/java/com/blog/blog_literario/services/email/PostModerationNotificationListenerTest.java
git commit -m "feat: add post moderation notification listener and email contract"
```

---

### Task 9: Resend template + full backend suite (marginalia-api)

**Files:**
- Modify: `src/main/java/com/blog/blog_literario/services/email/ResendEmailService.java` (replace the Task 8 stub)
- Test: `src/test/java/com/blog/blog_literario/services/email/ResendEmailServiceTest.java` (add tests)

**Interfaces:**
- Consumes: `sendWithRetry(CreateEmailOptions, RequestOptions, String)` and `emailProperties.logoUrl()` / `resendProperties.notificationsFrom()`, already present in the class.
- Produces: the final `sendPostModerationNotification` implementation.

- [ ] **Step 1: Add failing tests to `ResendEmailServiceTest`**

```java
    @Test
    void sendPostModerationNotification_published_buildsSubjectBodyAndIdempotencyKey() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendPostModerationNotification(
                "alice@test.com", "Alice", "Mi <cuento>",
                com.blog.blog_literario.model.PostStatus.DRAFT,
                com.blog.blog_literario.model.PostStatus.PUBLISHED,
                null, "http://front/user/posts", "post-moderation/10/abc");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        ArgumentCaptor<RequestOptions> requestCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(emails).send(optionsCaptor.capture(), requestCaptor.capture());

        assertThat(optionsCaptor.getValue().getFrom()).isEqualTo("Marginalia <avisos@test.dev>");
        assertThat(optionsCaptor.getValue().getTo()).containsExactly("alice@test.com");
        assertThat(optionsCaptor.getValue().getSubject()).isEqualTo("Tu post fue publicado en Marginalia");
        // User-authored title must be escaped in the HTML part.
        assertThat(optionsCaptor.getValue().getHtml()).contains("Mi &lt;cuento&gt;");
        assertThat(optionsCaptor.getValue().getHtml()).doesNotContain("Mi <cuento>");
        assertThat(optionsCaptor.getValue().getHtml()).contains("http://front/user/posts");
        // Old and new status names appear in both parts.
        assertThat(optionsCaptor.getValue().getHtml()).contains("Borrador").contains("Publicado");
        assertThat(optionsCaptor.getValue().getText()).contains("Borrador").contains("Publicado");
        assertThat(optionsCaptor.getValue().getText()).contains("http://front/user/posts");
        assertThat(requestCaptor.getValue().getIdempotencyKey()).isEqualTo("post-moderation/10/abc");
    }

    @Test
    void sendPostModerationNotification_rejectedWithNote_includesEscapedNote() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendPostModerationNotification(
                "alice@test.com", "Alice", "Mi cuento",
                com.blog.blog_literario.model.PostStatus.PUBLISHED,
                com.blog.blog_literario.model.PostStatus.REJECTED,
                "Revisa <b>ortografía</b>", "http://front/user/posts", "post-moderation/11/def");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture(), any(RequestOptions.class));

        assertThat(optionsCaptor.getValue().getSubject()).isEqualTo("Tu post necesita cambios");
        assertThat(optionsCaptor.getValue().getHtml()).contains("Revisa &lt;b&gt;ortograf");
        assertThat(optionsCaptor.getValue().getText()).contains("Revisa <b>ortografía</b>");
    }
```

- [ ] **Step 2: Run test to verify the new tests fail**

Run: `.\mvnw.cmd test "-Dtest=ResendEmailServiceTest"`
Expected: new tests FAIL with `UnsupportedOperationException` (Task 8 stub); pre-existing tests PASS.

- [ ] **Step 3: Replace the stub with the real implementation**

In `ResendEmailService.java`, replace the Task 8 stub with (import `com.blog.blog_literario.model.PostStatus`):

```java
    @Override
    public void sendPostModerationNotification(String to, String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote,
            String postsUrl, String idempotencyKey) {

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(resendProperties.notificationsFrom())
                .to(to)
                .subject(moderationSubject(newStatus))
                .html(buildModerationHtmlBody(authorName, postTitle, previousStatus, newStatus,
                        moderationNote, postsUrl))
                .text(buildModerationTextBody(authorName, postTitle, previousStatus, newStatus,
                        moderationNote, postsUrl))
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        sendWithRetry(params, options, "post moderation notification to " + to);
    }

    /** Subject/headline copy is driven by the post's NEW status. */
    private String moderationSubject(PostStatus newStatus) {
        return switch (newStatus) {
            case PUBLISHED -> "Tu post fue publicado en Marginalia";
            case REJECTED  -> "Tu post necesita cambios";
            case ARCHIVED  -> "Tu post fue archivado";
            case DRAFT     -> "Tu post volvió a borrador";
        };
    }

    /**
     * Author notification for a moderation status change, in the same
     * inline-styled single-column format as the other templates. The author's
     * name, post title, and moderation note are user-authored — always escaped.
     */
    private String buildModerationHtmlBody(String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote, String postsUrl) {
        String safeName = HtmlUtils.htmlEscape(authorName);
        String safeTitle = HtmlUtils.htmlEscape(postTitle);
        String safeUrl = HtmlUtils.htmlEscape(postsUrl);
        String safeLogoUrl = HtmlUtils.htmlEscape(emailProperties.logoUrl());
        String subject = HtmlUtils.htmlEscape(moderationSubject(newStatus));

        String noteBlock = (moderationNote == null || moderationNote.isBlank()) ? "" : """
                <p style="margin: 0 0 8px 0; font-size: 13px; text-transform: uppercase; letter-spacing: 1px; color: #a8a29e;">Nota de moderaci&oacute;n</p>
                <p style="margin: 0 0 28px 0; padding: 12px 16px; border-left: 3px solid #e7e5e4; font-size: 15px; line-height: 1.6; color: #57534e; font-style: italic;">%s</p>
                """.formatted(HtmlUtils.htmlEscape(moderationNote));

        return """
                <div style="display: none; max-height: 0; overflow: hidden; mso-hide: all;">%s</div>
                <div style="background-color: #faf8f5; padding: 40px 16px; font-family: Georgia, 'Times New Roman', serif;">
                  <div style="max-width: 520px; margin: 0 auto;">
                    <img src="%s" alt="Marginalia" width="120" style="display: block; margin: 0 auto 28px auto; border: 0;">
                    <p style="margin: 0 0 6px 0; text-align: center; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; color: #a8a29e;">&mdash; Moderaci&oacute;n &mdash;</p>
                    <h1 style="margin: 0 0 24px 0; text-align: center; font-size: 26px; font-weight: normal; color: #1c1917;">%s</h1>
                    <p style="margin: 0 0 8px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Hola %s,</p>
                    <p style="margin: 0 0 28px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Tu post <strong>%s</strong> cambi&oacute; de estado: %s &rarr; <strong>%s</strong>.</p>
                    %s<p style="margin: 0 0 28px 0; text-align: center;">
                      <a href="%s" style="display: inline-block; background-color: #be163d; color: #ffffff; padding: 12px 28px; font-size: 15px; text-decoration: none; border-radius: 4px;">Ver mis posts</a>
                    </p>
                    <div style="border-top: 1px solid #e7e5e4; margin: 0 0 16px 0;"></div>
                    <p style="margin: 0; font-size: 12px; line-height: 1.6; color: #a8a29e;">Recibes este mensaje porque eres autor en Marginalia. Puedes desactivar estos correos en Configuraci&oacute;n.</p>
                  </div>
                </div>
                """.formatted(subject, safeLogoUrl, subject, safeName, safeTitle,
                        previousStatus.getDisplayName(), newStatus.getDisplayName(),
                        noteBlock, safeUrl);
    }

    private String buildModerationTextBody(String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote, String postsUrl) {
        String noteBlock = (moderationNote == null || moderationNote.isBlank()) ? "" : """

                Nota de moderación:
                %s
                """.formatted(moderationNote);
        return """
                Hola %s,

                Tu post "%s" cambió de estado: %s → %s.
                %s
                Ver tus posts:

                %s

                Recibes este mensaje porque eres autor en Marginalia. Puedes desactivar estos correos en Configuración.
                """.formatted(authorName, postTitle,
                        previousStatus.getDisplayName(), newStatus.getDisplayName(),
                        noteBlock, postsUrl);
    }
```

- [ ] **Step 4: Run the email test class**

Run: `.\mvnw.cmd test "-Dtest=ResendEmailServiceTest"`
Expected: PASS (all tests, new and pre-existing).

- [ ] **Step 5: Run the FULL backend suite (regression gate)**

Run: `.\mvnw.cmd test`
Expected: BUILD SUCCESS. If ANY pre-existing test fails or needed its expectations changed, STOP and report it verbatim — never silently adjust.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/blog/blog_literario/services/email/ResendEmailService.java src/test/java/com/blog/blog_literario/services/email/ResendEmailServiceTest.java
git commit -m "feat: implement post moderation email template in ResendEmailService"
```

---

### Task 10: Branch + shadcn Switch primitive (marginalia-web)

**Files:**
- Create: `src/components/ui/switch.jsx`
- Modify: `package.json` / `package-lock.json` (via npm install only)

**Interfaces:**
- Produces: `<Switch checked disabled onCheckedChange id />` (re-export of Radix Switch styled for this design system) consumed by Task 13's SettingsPage.

- [ ] **Step 1: Create the branch (do NOT stage the pre-existing dirty files)**

```powershell
git -C C:\repos\BlogProyecto\marginalia-web checkout -b feature/moderation-notifications
```

Expected: `Switched to a new branch 'feature/moderation-notifications'` (uncommitted `.gitignore` / `marginalia-guia-de-voz.md` changes carry over untouched — leave them alone).

- [ ] **Step 2: Install the Radix dependency**

Run (in `C:\repos\BlogProyecto\marginalia-web`): `npm install @radix-ui/react-switch`
Expected: `added 1 package` (or similar), exit code 0.

- [ ] **Step 3: Create the Switch primitive**

`src/components/ui/switch.jsx`:

```jsx
import * as SwitchPrimitive from "@radix-ui/react-switch";
import { cn } from "@/lib/utils";

function Switch({ className, ...props }) {
  return (
    <SwitchPrimitive.Root
      data-slot="switch"
      className={cn(
        "peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border border-transparent transition-colors outline-none",
        "focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
        "disabled:cursor-not-allowed disabled:opacity-50",
        "data-[state=checked]:bg-rose-700 data-[state=unchecked]:bg-muted",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        data-slot="switch-thumb"
        className="pointer-events-none block size-4 rounded-full bg-background shadow-lg ring-0 transition-transform data-[state=checked]:translate-x-4 data-[state=unchecked]:translate-x-0.5"
      />
    </SwitchPrimitive.Root>
  );
}

export { Switch };
```

- [ ] **Step 4: Verify the build still compiles**

Run: `npm run build`
Expected: build completes without errors.

- [ ] **Step 5: Commit**

```powershell
git add src/components/ui/switch.jsx package.json package-lock.json
git commit -m "feat: add shadcn Switch primitive"
```

---

### Task 11: preferencesService (marginalia-web)

**Files:**
- Create: `src/features/settings/services/preferencesService.js`
- Test: `src/features/settings/services/preferencesService.test.js`

**Interfaces:**
- Consumes: `apiClient.get(endpoint)` / `apiClient.put(endpoint, body)` from `@/lib/apiClient`.
- Produces: `preferencesService.getPreferences()` → resolved map; `preferencesService.updatePreferences(changes)` → resolved map; constant `PREF_POST_MODERATION = "notifications.post-moderation"`.

- [ ] **Step 1: Write the failing test**

```js
import { describe, expect, it, vi } from "vitest"
import { apiClient } from "@/lib/apiClient"
import { PREF_POST_MODERATION, preferencesService } from "./preferencesService"

vi.mock(import("@/lib/apiClient"), () => ({
  apiClient: { get: vi.fn(), put: vi.fn() },
}))

describe("preferencesService", () => {
  it("exposes the post-moderation preference key", () => {
    expect(PREF_POST_MODERATION).toBe("notifications.post-moderation")
  })

  it("getPreferences fetches the resolved map", async () => {
    apiClient.get.mockResolvedValueOnce({ "notifications.post-moderation": "true" })

    const result = await preferencesService.getPreferences()

    expect(apiClient.get).toHaveBeenCalledWith("/me/preferences")
    expect(result).toEqual({ "notifications.post-moderation": "true" })
  })

  it("updatePreferences puts the partial map and returns the resolved map", async () => {
    apiClient.put.mockResolvedValueOnce({ "notifications.post-moderation": "false" })

    const result = await preferencesService.updatePreferences({
      "notifications.post-moderation": "false",
    })

    expect(apiClient.put).toHaveBeenCalledWith("/me/preferences", {
      "notifications.post-moderation": "false",
    })
    expect(result).toEqual({ "notifications.post-moderation": "false" })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/features/settings/services/preferencesService.test.js`
Expected: FAIL — cannot resolve `./preferencesService`.

- [ ] **Step 3: Write the service**

```js
import { apiClient } from "@/lib/apiClient";

const BASE_ENDPOINT = "/me/preferences";

/** Wire key of the "email me on moderation status changes" preference. */
export const PREF_POST_MODERATION = "notifications.post-moderation";

export const preferencesService = {
  // The backend always returns the fully resolved map (defaults merged in).
  getPreferences: () => apiClient.get(BASE_ENDPOINT),

  updatePreferences: (changes) => apiClient.put(BASE_ENDPOINT, changes),
};
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/features/settings/services/preferencesService.test.js`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/features/settings/services/preferencesService.js src/features/settings/services/preferencesService.test.js
git commit -m "feat: add preferences service"
```

---

### Task 12: usePreferences hook (marginalia-web)

**Files:**
- Create: `src/features/settings/hooks/usePreferences.js`
- Test: `src/features/settings/hooks/usePreferences.test.js`

**Interfaces:**
- Consumes: `preferencesService` (Task 11), `getErrorMessage` from `@/lib/apiError`, `toast` from `sonner`.
- Produces: `usePreferences()` → `{ preferences, loading, saving, error, reload, toggle }` where `preferences` is the string map (or `null` while loading), and `toggle(key)` flips `"true"`/`"false"` optimistically, reverting with a toast on failure.

- [ ] **Step 1: Write the failing test**

```js
import { act, renderHook, waitFor } from "@testing-library/react"
import { toast } from "sonner"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { preferencesService } from "@/features/settings/services/preferencesService"
import { usePreferences } from "./usePreferences"

vi.mock(import("@/features/settings/services/preferencesService"), () => ({
  PREF_POST_MODERATION: "notifications.post-moderation",
  preferencesService: { getPreferences: vi.fn(), updatePreferences: vi.fn() },
}))

vi.mock(import("sonner"), () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

const KEY = "notifications.post-moderation"

describe("usePreferences", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("loads the resolved preferences on mount", async () => {
    preferencesService.getPreferences.mockResolvedValueOnce({ [KEY]: "true" })

    const { result } = renderHook(() => usePreferences())

    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.preferences).toEqual({ [KEY]: "true" })
    expect(result.current.error).toBeNull()
  })

  it("exposes an error message when loading fails", async () => {
    preferencesService.getPreferences.mockRejectedValueOnce(new Error("boom"))

    const { result } = renderHook(() => usePreferences())

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBeTruthy()
    expect(result.current.preferences).toBeNull()
  })

  it("toggle applies the server's resolved map on success", async () => {
    preferencesService.getPreferences.mockResolvedValueOnce({ [KEY]: "true" })
    preferencesService.updatePreferences.mockResolvedValueOnce({ [KEY]: "false" })

    const { result } = renderHook(() => usePreferences())
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.toggle(KEY)
    })

    expect(preferencesService.updatePreferences).toHaveBeenCalledWith({ [KEY]: "false" })
    expect(result.current.preferences[KEY]).toBe("false")
  })

  it("toggle reverts the optimistic value and toasts on failure", async () => {
    preferencesService.getPreferences.mockResolvedValueOnce({ [KEY]: "true" })
    preferencesService.updatePreferences.mockRejectedValueOnce(new Error("boom"))

    const { result } = renderHook(() => usePreferences())
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.toggle(KEY)
    })

    expect(result.current.preferences[KEY]).toBe("true")
    expect(toast.error).toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/features/settings/hooks/usePreferences.test.js`
Expected: FAIL — cannot resolve `./usePreferences`.

- [ ] **Step 3: Write the hook**

```js
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { preferencesService } from "@/features/settings/services/preferencesService";
import { getErrorMessage } from "@/lib/apiError";

export function usePreferences() {
  const [preferences, setPreferences] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPreferences(await preferencesService.getPreferences());
    } catch (err) {
      setError(getErrorMessage(err, "No se pudieron cargar tus preferencias."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const toggle = useCallback(
    async (key) => {
      if (!preferences || saving) return;
      const previous = preferences[key];
      const next = previous === "true" ? "false" : "true";

      // Optimistic flip; the server's resolved map is authoritative on success.
      setPreferences((prev) => ({ ...prev, [key]: next }));
      setSaving(true);
      try {
        const resolved = await preferencesService.updatePreferences({ [key]: next });
        setPreferences(resolved);
      } catch (err) {
        setPreferences((prev) => ({ ...prev, [key]: previous }));
        toast.error(getErrorMessage(err, "No se pudo guardar tu preferencia."));
      } finally {
        setSaving(false);
      }
    },
    [preferences, saving],
  );

  return { preferences, loading, saving, error, reload: load, toggle };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/features/settings/hooks/usePreferences.test.js`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/features/settings/hooks/usePreferences.js src/features/settings/hooks/usePreferences.test.js
git commit -m "feat: add usePreferences hook with optimistic toggle"
```

---

### Task 13: SettingsPage with role gate (marginalia-web)

**Files:**
- Create: `src/features/settings/pages/SettingsPage.jsx`
- Test: `src/features/settings/pages/SettingsPage.test.jsx`

**Interfaces:**
- Consumes: `usePreferences` (Task 12), `PREF_POST_MODERATION` (Task 11), `Switch` (Task 10), `useAuth` (`state.user.role`), `ROLE_LEVEL` from `@/utils/roles`, `EmptyState` / `PageError` from `@/shared/components`, `Label` / `Skeleton` from `@/components/ui`.
- Produces: default-export `SettingsPage` component for Task 14's route.

- [ ] **Step 1: Write the failing test**

```jsx
import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import SettingsPage from "./SettingsPage"

const { authState, prefsState } = vi.hoisted(() => ({
  authState: { user: { role: "AUTHOR" } },
  prefsState: {
    preferences: { "notifications.post-moderation": "true" },
    loading: false,
    saving: false,
    error: null,
    reload: vi.fn(),
    toggle: vi.fn(),
  },
}))

vi.mock(import("@/features/auth/hooks/useAuth"), () => ({
  useAuth: () => ({ state: authState }),
}))

vi.mock(import("@/features/settings/hooks/usePreferences"), () => ({
  usePreferences: () => prefsState,
}))

describe("SettingsPage", () => {
  beforeEach(() => {
    authState.user = { role: "AUTHOR" }
    prefsState.loading = false
    prefsState.error = null
  })

  it("shows the notifications section with the toggle for an AUTHOR", () => {
    render(<SettingsPage />)

    expect(screen.getByText("Notificaciones")).toBeInTheDocument()
    expect(screen.getByRole("switch")).toBeChecked()
  })

  it("shows the notifications section for roles above AUTHOR", () => {
    authState.user = { role: "ADMIN" }

    render(<SettingsPage />)

    expect(screen.getByText("Notificaciones")).toBeInTheDocument()
  })

  it("shows the empty state instead of the section for a READER", () => {
    authState.user = { role: "READER" }

    render(<SettingsPage />)

    expect(screen.queryByText("Notificaciones")).not.toBeInTheDocument()
    expect(screen.queryByRole("switch")).not.toBeInTheDocument()
    expect(screen.getByText("Aún no hay ajustes disponibles")).toBeInTheDocument()
  })

  it("renders the switch unchecked when the preference is off", () => {
    prefsState.preferences = { "notifications.post-moderation": "false" }

    render(<SettingsPage />)

    expect(screen.getByRole("switch")).not.toBeChecked()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/features/settings/pages/SettingsPage.test.jsx`
Expected: FAIL — cannot resolve `./SettingsPage`.

- [ ] **Step 3: Write the page**

```jsx
import { Settings as SettingsIcon } from "lucide-react";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { EmptyState } from "@/shared/components/EmptyState";
import { PageError } from "@/shared/components/PageError";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { ROLE_LEVEL } from "@/utils/roles";
import { usePreferences } from "@/features/settings/hooks/usePreferences";
import { PREF_POST_MODERATION } from "@/features/settings/services/preferencesService";

export default function SettingsPage() {
  const {
    state: { user },
  } = useAuth();
  const { preferences, loading, saving, error, reload, toggle } = usePreferences();

  // Sections that don't apply to the user's role are hidden entirely, never
  // rendered disabled. Today READERs have no applicable sections at all.
  const isAuthorOrAbove = (ROLE_LEVEL[user?.role] ?? 0) >= ROLE_LEVEL.AUTHOR;

  if (isAuthorOrAbove && error) {
    return (
      <PageError
        icon={SettingsIcon}
        title="No pudimos cargar tus preferencias"
        message={error}
        onRetry={reload}
      />
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-6 rounded-md border border-border bg-surface-warm p-6">
        <p className="text-xs font-semibold uppercase tracking-[0.22em] text-rose-700 dark:text-rose-400">
          Ajustes
        </p>
        <h1 className="mt-2 font-serif text-4xl text-foreground">Configuración</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Administra tus preferencias de la plataforma.
        </p>
      </div>

      {!isAuthorOrAbove ? (
        <EmptyState
          icon={SettingsIcon}
          title="Aún no hay ajustes disponibles"
          description="Por ahora no hay ajustes disponibles para tu cuenta. Cuando existan, aparecerán aquí."
        />
      ) : loading ? (
        <div className="rounded-md border border-border bg-card p-6">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="mt-4 h-5 w-full max-w-md" />
        </div>
      ) : (
        <section className="rounded-md border border-border bg-card p-6">
          <h2 className="font-serif text-2xl text-foreground">Notificaciones</h2>
          <div className="mt-4 flex items-center justify-between gap-4">
            <div>
              <Label
                htmlFor="pref-post-moderation"
                className="text-sm font-medium text-foreground"
              >
                Correos de moderación
              </Label>
              <p className="mt-1 text-sm text-muted-foreground">
                Recibir un correo cuando el estado de uno de mis posts cambie por moderación.
              </p>
            </div>
            <Switch
              id="pref-post-moderation"
              checked={preferences?.[PREF_POST_MODERATION] === "true"}
              disabled={saving}
              onCheckedChange={() => toggle(PREF_POST_MODERATION)}
            />
          </div>
        </section>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/features/settings/pages/SettingsPage.test.jsx`
Expected: PASS (4 tests). If `PageError` renders anything router-dependent and a test crashes with a router error, wrap `render(<SettingsPage />)` in `MemoryRouter` from `react-router-dom` — but only if actually needed.

- [ ] **Step 5: Commit**

```powershell
git add src/features/settings/pages/SettingsPage.jsx src/features/settings/pages/SettingsPage.test.jsx
git commit -m "feat: add SettingsPage with role-gated notifications section"
```

---

### Task 14: Route + TopBar wiring, full frontend suite (marginalia-web)

**Files:**
- Modify: `src/routes/AppRouter.jsx` (add route next to `profile`, ~line 86)
- Modify: `src/panel/layout/TopBar.jsx` (replace disabled item, lines 99–102)

**Interfaces:**
- Consumes: `SettingsPage` (Task 13).
- Produces: `/user/settings` route inside `AdminLayout`; enabled "Configuración" dropdown item.

- [ ] **Step 1: Add the route**

In `src/routes/AppRouter.jsx`, add the import:

```jsx
import SettingsPage from "@/features/settings/pages/SettingsPage.jsx";
```

and inside the `AdminLayout` children, directly after the `profile` route:

```jsx
              { path: "settings", element: <SettingsPage /> },
```

- [ ] **Step 2: Enable the TopBar item**

In `src/panel/layout/TopBar.jsx`, replace:

```jsx
            <DropdownMenuItem disabled className="gap-2">
              <Settings size={17} />
              <span>Configuración</span>
            </DropdownMenuItem>
```

with (mirroring the "Perfil" item above it):

```jsx
            <DropdownMenuItem asChild>
              <Link to="settings" className="flex w-full cursor-pointer items-center gap-2">
                <Settings size={17} />
                <span>Configuración</span>
              </Link>
            </DropdownMenuItem>
```

- [ ] **Step 3: Run the FULL frontend suite and lint (regression gate)**

Run: `npm run test`
Expected: all tests pass (new settings tests plus every pre-existing test). If any pre-existing test fails, STOP and report it verbatim — never silently adjust.

Run: `npm run lint`
Expected: exit code 0, no errors.

- [ ] **Step 4: Commit**

```powershell
git add src/routes/AppRouter.jsx src/panel/layout/TopBar.jsx
git commit -m "feat: wire /user/settings route and enable Configuración menu item"
```

---

## Final verification (manual E2E, after all tasks)

With the backend on `dev` profile (`email.provider=logging`) and the frontend dev server (beware a devtools instance may squat :8080):

1. Backend boots cleanly against dev Postgres — Flyway applies V11 and `ddl-auto=validate` accepts the new entity.
2. Log in as a moderator/admin, approve or reject another user's DRAFT post in `/user/moderacion` → backend log shows `[email.provider=logging] Post moderation notification for <author> ...` with correct old → new statuses.
3. As the author, open the user dropdown → "Configuración" is enabled → `/user/settings` shows the Notificaciones card with the switch ON. Turn it OFF, reload the page → still OFF (`GET /api/me/preferences` returns `"false"`).
4. Repeat a moderation action on that author's post → NO log line.
5. Moderate your OWN post (admin) → NO log line (self-notification skip).
6. Log in as a READER → `/user/settings` shows the EmptyState, no switch.
