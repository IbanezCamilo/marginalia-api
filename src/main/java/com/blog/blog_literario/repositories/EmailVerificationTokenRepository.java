package com.blog.blog_literario.repositories;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.EmailVerificationToken;
import com.blog.blog_literario.model.User;

/**
 * Repository for {@link EmailVerificationToken} persistence.
 *
 * <p>Provides lookup by token hash plus the queries behind the resend policy:
 * the latest token per user drives the cooldown, the recent-token count drives
 * the daily cap, and bulk deletions cover verification success and scheduled purge.
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    Optional<EmailVerificationToken> findTopByUserOrderByCreatedAtDesc(User user);

    long countByUserAndCreatedAtAfter(User user, LocalDateTime threshold);

    void deleteByUser(User user);

    void deleteByExpiresAtBefore(LocalDateTime threshold);
}
