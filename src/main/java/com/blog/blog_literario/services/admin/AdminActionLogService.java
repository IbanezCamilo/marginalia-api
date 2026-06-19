package com.blog.blog_literario.services.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.model.AdminActionLog;
import com.blog.blog_literario.repositories.AdminActionLogRepository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Records an immutable audit trail entry for admin actions that override a
 * business-flow rule (forced status transitions, permanent-block resets, post/user
 * deletion, password resets, role changes) so the bypass is always attributable to a
 * specific admin, action, and reason.
 */
@Service
@RequiredArgsConstructor
public class AdminActionLogService {

    private final AdminActionLogRepository adminActionLogRepository;

    @Transactional
    public void record(
            @NonNull Integer adminId,
            @NonNull String adminEmail,
            @NonNull String actionType,
            @NonNull String targetType,
            @NonNull Integer targetId,
            String details) {

        AdminActionLog log = new AdminActionLog();
        log.setAdminId(adminId);
        log.setAdminEmail(adminEmail);
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(details);

        adminActionLogRepository.save(log);
    }
}
