package com.blog.blog_literario.services.secundary;

import org.springframework.security.core.userdetails.UserDetails;

import com.blog.blog_literario.dto.profile.userProfileResponseDTO;
import com.blog.blog_literario.dto.profile.userProfileUpdateDTO;

public interface UserProfileService {

    userProfileResponseDTO getUserProfile(UserDetails userDetails);

    userProfileResponseDTO updateUserProfile(UserDetails userDetails, userProfileUpdateDTO dto);
}
