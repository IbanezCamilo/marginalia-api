package com.blog.blog_literario.config;

import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.blog.blog_literario.config.properties.AdminProperties;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the database with required reference data on startup.
 *
 * <p>Creates the five application roles (READER, AUTHOR, MODERATOR, ADMIN, OWNER) and
 * the seed user if they do not already exist. Credentials are sourced from
 * {@link AdminProperties} ({@code admin.*} in {@code application.yml}). If the seed
 * email already exists in the database (e.g. a production deploy that previously
 * seeded it as ADMIN before OWNER existed), its role is explicitly upgraded to OWNER
 * rather than left untouched — OWNER is never assignable through any endpoint, so this
 * startup upgrade is the only way an existing deployment ever gets one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    public void run(String... args) throws Exception {
        log.info("Executing DataInitializer");
        RoleInitializer();
        UserAdminInitializer();
    }

    private void RoleInitializer() {
        createRoleIfNotExist(Role.READER);
        createRoleIfNotExist(Role.AUTHOR);
        createRoleIfNotExist(Role.MODERATOR);
        createRoleIfNotExist(Role.ADMIN);
        createRoleIfNotExist(Role.OWNER);
    }

    private void createRoleIfNotExist(String RoleName) {
        if (roleRepository.findByName(RoleName).isEmpty()) {
            Role newRole = new Role(RoleName);
            roleRepository.save(newRole);
            log.info("Role '{}' created", RoleName);
        } else {
            log.debug("Role '{}' already exists", RoleName);
        }
    }

    private void UserAdminInitializer() {
        Role ownerRole = roleRepository.findByName(Role.OWNER)
                .orElseThrow(() -> new RuntimeException("ERROR: El rol owner no existe"));

        Optional<User> existing = userRepository.findByEmail(adminProperties.email());

        if (existing.isEmpty()) {
            User owner = new User();
            owner.setName("Propietario");
            owner.setEmail(adminProperties.email());
            owner.setPassword(passwordEncoder.encode(adminProperties.password()));
            owner.setRole(ownerRole);
            owner.setProfilePicture(null);

            userRepository.save(owner);
            log.info("Owner user created with email: {}", adminProperties.email());
            return;
        }

        User user = existing.get();
        if (user.getRole().isOwner()) {
            log.debug("Owner user already exists with OWNER role");
            return;
        }

        String previousRoleName = user.getRole().getName();
        user.setRole(ownerRole);
        user.incrementTokenVersion();
        userRepository.save(user);
        log.info("Upgraded existing seed user '{}' from {} to OWNER", adminProperties.email(), previousRoleName);
    }

}
