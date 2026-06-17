package com.blog.blog_literario.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.config.properties.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

/**
 * Stateless JWT service: issues tokens and validates them on incoming requests.
 *
 * <p>The signing key and expiration duration are read from {@link JwtProperties}
 * (bound to {@code app.jwt.*} in {@code application.yml}).
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a signed JWT for the given user, embedding {@code tokenVersion} so
     * {@link #isTokenValid} can reject it once the user's role or password changes.
     *
     * @param userDetails  the authenticated user (username = email)
     * @param tokenVersion the user's current {@code tokenVersion} at issuance time
     * @return a compact, signed JWT string
     */
    public String generateToken(UserDetails userDetails, Integer tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_VERSION_CLAIM, tokenVersion);
        return generateToken(claims, userDetails);
    }

    /**
     * Generates a signed JWT with additional custom claims merged into the payload.
     *
     * @param extraClaims additional claims to embed in the token body
     * @param userDetails the authenticated user
     * @return a compact, signed JWT string
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.expiration()))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the {@code sub} claim (user email) from a token without verifying expiry.
     *
     * @param token a compact JWT string
     * @return the subject (email) embedded in the token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the {@code tokenVersion} claim embedded at issuance time.
     */
    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get(TOKEN_VERSION_CLAIM, Integer.class));
    }

    /**
     * Returns {@code true} if the token signature is valid, the subject matches
     * {@code userDetails}, the token has not expired, and its {@code tokenVersion}
     * claim still matches the user's current value in the database. The last check
     * makes a role or password change reject any access token issued before it,
     * even though that token remains cryptographically valid until its natural expiry.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername()) || isTokenExpired(token)) {
            return false;
        }

        Integer tokenVersion = extractTokenVersion(token);
        Integer currentVersion = (userDetails instanceof UserDetailsImpl impl)
                ? impl.getUser().getTokenVersion()
                : null;

        return tokenVersion != null && tokenVersion.equals(currentVersion);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts the {@code sub} claim from a token that may be expired.
     * Used during logout to identify the user even when the access token has lapsed.
     */
    public String extractUsernameAllowExpired(String token) {
        try {
            return extractClaim(token, Claims::getSubject);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return e.getClaims().getSubject();
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
