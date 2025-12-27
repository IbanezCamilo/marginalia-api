package com.blog.blog_literario.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 150)
    private String slug;

    public Category(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public boolean isValid() {
        return name != null && !name.isBlank()
                && slug != null && !slug.isBlank();
    }
}
