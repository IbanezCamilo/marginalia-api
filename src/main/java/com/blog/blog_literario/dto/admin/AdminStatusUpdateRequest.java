package com.blog.blog_literario.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminStatusUpdateRequest(
        @NotBlank(message = "El estado no puede estar vacio")
        String status,
        //Reason is optional, but if provided, it should not be blank
        String reason
        ) {

}
