package com.blog.blog_literario.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;


/**
 * Loads a {@link User} by email for Spring Security's authentication process.
 * Email is used as the username throughout this application.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Looks up a user by email and wraps them in a {@link UserDetailsImpl}.
     *
     * @param email the user's email address (Spring Security calls this "username")
     * @throws UsernameNotFoundException if no user with the given email exists
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        return new UserDetailsImpl(user);
    }
}
