package com.blog.blog_literario.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {
    private static final long EXPIRATION_TIME = 1000 * 60 * 60* 24; // 24 horas
    private final Key secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256); // Clave secreta para firmar el JWT
    //private final Key secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256); // Clave secreta para firmar el JWT

    //Método para generar JWT sin claims extras
    public String generateToken(UserDetails userDetails){
        return generateToken(new HashMap<>(), userDetails);
    }
    
   //Sobrecarga del Método para generar tokens
   public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails){
        return Jwts.builder()
                .setClaims(extraClaims) // Establece los claims extras
                .setSubject(userDetails.getUsername()) // Establece el sujeto del token
                .setIssuedAt(new Date(System.currentTimeMillis())) // Establece la fecha de emisión
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // Establece la fecha de expiración
                .signWith(secretKey) // Firma el token con la clave secreta
                .compact(); // Compacta el JWT en una cadena
   }
   
   //Extra el username (email) desde el token
   public String extractUsername(String token){
    return extractClaim(token, Claims::getSubject);
   }

    // Verifica si el token es válido para el usuario
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // Comprueba si el token expiró
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Extrae cualquier claim usando una función
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Decodifica y valida la firma del token
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
