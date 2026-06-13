package com.blog.blog_literario.config;

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
 * <p>Creates the four application roles (READER, AUTHOR, MODERATOR, ADMIN) and the
 * default admin user if they do not already exist. Credentials are sourced from
 * {@link AdminProperties} ({@code admin.*} in {@code application.yml}).
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
        if (userRepository.findByEmail(adminProperties.email()).isEmpty()) {
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new RuntimeException("ERROR: El rol admin no existe"));

            User admin = new User();
            admin.setName("Administrador Principal");
            admin.setEmail(adminProperties.email());
            admin.setPassword(passwordEncoder.encode(adminProperties.password()));
            admin.setRole(adminRole);
            admin.setProfilePicture(null);

            userRepository.save(admin);
            log.info("Admin user created with email: {}", adminProperties.email());
        } else {
            log.info("Admin user already exists");
        }
    }

}
