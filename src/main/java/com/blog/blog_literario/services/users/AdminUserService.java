package com.blog.blog_literario.services.users;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.users.CreateUserRequest;
import com.blog.blog_literario.dto.users.UpdateUserRequest;
import com.blog.blog_literario.dto.users.UserResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // ─── Mapper ────────────────────────────────────────────────────────────────
    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    // ─── Queries ────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(@NonNull Integer id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con ID: " + id));
    }

    // ─── Commands ────────────────────────────────────────────────────────────────
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        Role role = roleRepository.findByName(request.role().getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                "Rol no encontrado: " + request.role()));

        User newUser = new User();
        newUser.setName(request.name());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setRole(role);
        newUser.setProfilePicture("https://servidor.com/images/default-avatar.png");

        userRepository.save(newUser);
        return toResponse(newUser);
    }

    public UserResponse update(@NonNull Integer id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                "Usuario no encontrado con ID: " + id));

        Role role = roleRepository.findByName(request.role().getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                "Rol no encontrado: " + request.role()));

        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(role);

        return toResponse(userRepository.save(user));
    }

    public void delete(@NonNull Integer id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("No se encontró el usuario con ID: " + id);
        }
        userRepository.deleteById(id);
    }

}
