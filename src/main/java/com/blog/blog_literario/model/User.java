package com.blog.blog_literario.model;

import jakarta.persistence.*; // Contiene todas las anotaciones de JPA
import lombok.AllArgsConstructor;
import lombok.Data;// Lombok para generar getters y setters automáticamente
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users", // ✅ Plural según convención
        indexes = {
            @Index(name = "idx_user_email", columnList = "email"),
            @Index(name = "idx_user_role", columnList = "role_id")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "profile_picture", nullable = false, length = 500)
    private String profilePicture;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    // RELATIONSHIPS
    @ManyToOne(fetch = FetchType.EAGER) // ✅ EAGER porque siempre necesitas el rol
    @JoinColumn(name = "role_id", nullable = false) // ✅ Consistente con otras FKs
    private Role role;

    // UTILITY METHODS
    /**
     * Método para verificar si el usuario es admin
     */
    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }

    /**
     * Método para verificar si el usuario es autor
     */
    public boolean isAuthor() {
        return role != null && role.isAuthor();
    }

    /**
     * Método para obtener nombre de rol (útil en vistas)
     */
    public String getRoleName() {
        return role != null ? role.getName() : "UNKNOWN";
    }

    /**
     * Constructor sin password (útil para respuestas)
     */
    public User(Integer id, String name, String email, Role role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }
}
