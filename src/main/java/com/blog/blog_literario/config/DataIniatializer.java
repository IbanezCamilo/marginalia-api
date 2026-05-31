package com.blog.blog_literario.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataIniatializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_EMAIL}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("---------Executing DataInitializer--------");
        RoleInitializer();
        UserAdminInitializer();

    }

    private void RoleInitializer() {
        //creating roles
        createRoleIfNotExist(Role.READER);
        createRoleIfNotExist(Role.AUTHOR);
        createRoleIfNotExist(Role.MODERATOR);
        createRoleIfNotExist(Role.ADMIN);

    }

    private void createRoleIfNotExist(String RoleName) {
        //Verifying the rol
        if (roleRepository.findByName(RoleName).isEmpty()) {
            Role newRole = new Role(RoleName);
            roleRepository.save(newRole);
            System.out.println("rol " + RoleName + " created");
        } else {
            System.out.println("rol " + RoleName + " exist already");
        }
    }

    private void UserAdminInitializer() {
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            //Creating the admin user
            Role adminRole = roleRepository.findByName("ADMIN").orElseThrow(() -> new RuntimeException("ERROR: El rol admin no existe"));

            //Creating the admin user
            User admin = new User();
            admin.setName("Administrador Principal");
            admin.setEmail(adminEmail);
            String encryptedPassword = passwordEncoder.encode(adminPassword);
            admin.setPassword(encryptedPassword);
            admin.setRole(adminRole);
            admin.setProfilePicture(null);

            userRepository.save(admin);

            System.out.println("------------Usuario ADMIN creado-----------");
            System.out.println("---------Email: " + adminEmail + "--------------");

        } else {
            System.out.println("---------------Usuario ADMIN ya existe----------------");
        }
    }

}
