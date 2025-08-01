package com.blog.blog_literario.services.secundary;

import org.springframework.security.core.userdetails.UserDetails;

import com.blog.blog_literario.dto.profile.userProfileResponseDTO;
import com.blog.blog_literario.dto.profile.userProfileUpdateDTO;

public interface UserProfileService {
    //MÉTODOS DEL PERFIL-USUARIO 
   userProfileResponseDTO getUserProfile(UserDetails userDetails);//Método plantilla para mostrar perfil
   userProfileResponseDTO updateUserProfile(UserDetails userDetails, userProfileUpdateDTO dto); //Método plantilla para actualizar perfil
}
