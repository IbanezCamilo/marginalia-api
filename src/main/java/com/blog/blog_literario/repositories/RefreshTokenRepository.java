package com.blog.blog_literario.repositories;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blog.blog_literario.model.RefreshToken;
import com.blog.blog_literario.model.User;

/**
 * Repository for {@link RefreshToken} persistence.
 *
 * <p>Provides lookup by token value and bulk deletion by user,
 * used during token rotation and logout.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);

    void deleteByUserAndExpiresAtBefore(User user, LocalDateTime threshold);

    @Query("SELECT COUNT(rt) > 0 FROM RefreshToken rt WHERE rt.user.email = :email AND rt.expiresAt > :now")
    boolean existsActiveByUserEmail(@Param("email") String email, @Param("now") LocalDateTime now);
}