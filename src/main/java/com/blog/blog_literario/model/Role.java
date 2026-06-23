package com.blog.blog_literario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Role entity for user authorization.
 *
 * Defines user roles (ADMIN, AUTHOR, READER) for access control. Each user must
 * have exactly one role.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "roles",
        indexes = {
            @Index(name = "idx_role_name", columnList = "name")
        }
)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    public static final String READER = "READER";
    public static final String AUTHOR = "AUTHOR";
    public static final String MODERATOR = "MODERATOR";
    public static final String ADMIN = "ADMIN";
    public static final String OWNER = "OWNER";

    /** Creates a role with only the name set; used during data seeding. */
    public Role(String name) {
        this.name = name;
    }


    public boolean isAdmin() {
        return ADMIN.equalsIgnoreCase(name);
    }


    public boolean isAuthor() {
        return AUTHOR.equalsIgnoreCase(name);
    }


    public boolean isOwner() {
        return OWNER.equalsIgnoreCase(name);
    }
}
