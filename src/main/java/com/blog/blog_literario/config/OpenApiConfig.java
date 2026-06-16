package com.blog.blog_literario.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI blogLiterarioOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Marginalia Blog API")
                        .version("1.0.0")
                        .description("REST API for the Marginalia literary blog. "
                                + "Authentication uses an HttpOnly JWT cookie — "
                                + "browser restrictions prevent setting it directly from Swagger UI."))
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("jwt")));
    }
}
