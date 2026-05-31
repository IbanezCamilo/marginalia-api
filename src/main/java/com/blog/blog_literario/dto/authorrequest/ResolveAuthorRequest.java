package com.blog.blog_literario.dto.authorrequest;

/**
 * Request body for approving or rejecting an author request.
 * adminNote is optional on approval but recommended on rejection
 * so the user understands why and can improve before reapplying.
 */
public record ResolveAuthorRequest(
        String adminNote
) {}