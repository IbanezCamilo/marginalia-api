package com.blog.blog_literario.dto.authDTO;
/* 
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
*/
public record authResponseDTO( //Devuelve el token al cliente una vez autenticado
    String token
){}
