package com.blog.blog_literario.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Role entity for user authorization
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
    @Column(name = "id") // ✅ Simplificado
    private Integer id;

    @Column(name = "name", nullable = false, unique = true, length = 50) // ✅ Longitud definida
    private String name;

    // CONSTANTS (Opcional pero muy útil)
    /**
     * ✅ Constantes para roles comunes Evita typos y facilita uso en código
     */
    public static final String ADMIN = "ADMIN";
    public static final String AUTHOR = "AUTHOR";
    public static final String READER = "READER";

    // UTILITY METHODS
    /**
     * Constructor de conveniencia
     */
    public Role(String name) {
        this.name = name;
    }


    public boolean isAdmin() {
        return ADMIN.equalsIgnoreCase(name);
    }


    public boolean isAuthor() {
        return AUTHOR.equalsIgnoreCase(name);
    }
}
