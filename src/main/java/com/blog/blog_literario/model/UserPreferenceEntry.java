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
