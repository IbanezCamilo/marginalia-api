package com.blog.blog_literario.dto.authorrequest;

import java.time.LocalDateTime;

/**
 * Response DTO for AuthorRequest.
 * Used by both READER endpoints (/api/me/author-request)
 * and admin endpoints (/api/admin/author-requests).
 */
public record AuthorRequestResponse(
        Integer id,

        // Requester data
        Integer requesterId,
        String  requesterName,
        String  requesterEmail,

        // Request content
        String motivation,

        // Status
        String status,
        String statusDisplayName,

        // Resolution (null while PENDING)
        String        adminNote,
        String        resolvedByName,
        LocalDateTime resolvedAt,

        // Active review claim (all null when unclaimed, expired, or resolved —
        // the service nulls expired claims so clients never need to know the TTL)
        Integer       claimedById,
        String        claimedByName,
        LocalDateTime claimedAt,

        // Audit
        LocalDateTime createdAt
) {}