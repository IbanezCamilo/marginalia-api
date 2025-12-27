package com.blog.blog_literario.config;

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
    
    @Override
    public void run(String... args) throws Exception{
        System.out.println("---------Executing DataInitializer--------");

        RoleInitializer();

        UserAdminInitializer();

    }

    private void RoleInitializer(){
        //creating roles
        createRoleIfNotExist("READER");
        createRoleIfNotExist("AUTHOR");
        createRoleIfNotExist("ADMIN");

    }

    private void createRoleIfNotExist(String RoleName){
        //Verifying the rol
        if(roleRepository.findByName(RoleName).isEmpty()){
            Role newRole = new Role(RoleName);
            roleRepository.save(newRole);
            System.out.println("rol " + RoleName + " created");
        }else{
                        System.out.println("rol " + RoleName + " exist already");
        }
    }

    private void UserAdminInitializer(){
        String adminEmail = "REMOVED_BY_SECRET_SCAN";

        if(userRepository.findByEmail(adminEmail).isEmpty()){
            //Creating the admin user
            Role adminRole = roleRepository.findByName("ADMIN").orElseThrow(() -> new RuntimeException("ERROR: El rol admin no existe"));

        User admin = new User();
        admin.setName("Administrador Principal");
        admin.setEmail(adminEmail);

        //creating and encrypting the password
        String plainPassword = "REMOVED_BY_SECRET_SCAN";
        String encryptedPassword = passwordEncoder.encode(plainPassword);
        admin.setPassword(encryptedPassword);

        admin.setRole(adminRole);
        admin.setProfilePicture("https://images.theconversation.com/files/644172/original/file-20250122-15-kpleen.jpg?ixlib=rb-4.1.0&q=45&auto=format&w=1000&fit=clip");

        userRepository.save(admin);


                    System.out.println("------------Usuario ADMIN creado-----------");
                            System.out.println("---------Email: " + adminEmail + "--------------");

        }else{
                    System.out.println("---------------Usuario ADMIN ya existe----------------");
        }
    }
        
}
