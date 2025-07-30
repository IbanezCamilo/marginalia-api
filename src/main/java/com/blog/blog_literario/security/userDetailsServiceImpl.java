package com.blog.blog_literario.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class userDetailsServiceImpl implements UserDetailsService{
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException{
        User usuario = userRepository.findByEmail(email) // se valida que el usuario exista
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        //Guardar y retornar
        return new userDetailsImpl(usuario);
    }
}
