package com.blog.blog_literario.controllers.users;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.services.users.UserPreferenceService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints for an authenticated user to read and update their own preferences.
 * Responses are always the fully resolved map (defaults merged in), so the
 * frontend never computes defaults.
 */
@Tag(name = "My Preferences")
@SecurityRequirement(name = "cookieAuth")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/me/preferences")
public class MyPreferencesController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public ResponseEntity<Map<String, String>> getPreferences(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userPreferenceService.getResolved(userDetails));
    }

    /** Accepts a partial map of {@code key -> value}; unknown keys or invalid values → 400. */
    @PutMapping
    public ResponseEntity<Map<String, String>> updatePreferences(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> changes) {
        return ResponseEntity.ok(userPreferenceService.update(userDetails, changes));
    }
}
