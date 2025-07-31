package com.blog.blog_literario.security;

import com.blog.blog_literario.model.User; //Importa la Entidad USER
//interfaz que Spring Security necesita para reconocer y autenticar a un usuario.
import org.springframework.security.core.GrantedAuthority;
//representa permisos/roles del usuario.
import org.springframework.security.core.authority.SimpleGrantedAuthority;
//implementación sencilla de Spring Security basada en Strings, como "ROLE_USER".
import org.springframework.security.core.userdetails.UserDetails;

import lombok.RequiredArgsConstructor;

import java.util.Collection; //para Colecciones
import java.util.List; // para Listas

@RequiredArgsConstructor // genera automaticamente constructor con el campo final
public class userDetailsImpl implements UserDetails {
    private final User usuario;

    @Override // Lista para conocer los permisos de usuario
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return List.of(new SimpleGrantedAuthority(usuario.getRol().getNombre()));
    }

    @Override
    public String getPassword() {
        return usuario.getPassword(); // Retorna la contraseña del usuario
    }

    @Override
    public String getUsername() {
        return usuario.getEmail(); // Retorna el email para usar como usuario
    }

    // Opcional: Modificar según reglas de negocio
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    //Por si acaso
    public User getUser() {
    return usuario;
    }

}
