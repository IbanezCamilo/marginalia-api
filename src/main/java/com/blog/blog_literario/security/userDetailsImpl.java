package com.blog.blog_literario.security;

import java.util.Collection; //Importa la Entidad USER
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails; //para Colecciones

import com.blog.blog_literario.model.User; // para Listas


public class UserDetailsImpl implements UserDetails {

    private final User user;

    public UserDetailsImpl(User user) {
        this.user = user;
    }

    @Override // Lista para conocer los permisos de usuario
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
    }

    @Override
    public String getPassword() {
        return user.getPassword(); // Retorna la contraseña del usuario
    }

    @Override
    public String getUsername() {
        return user.getEmail(); // Retorna el email para usar como usuario
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
        return user;
    }

}
