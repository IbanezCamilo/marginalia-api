package com.blog.blog_literario.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately boots with no active profile so it exercises the base
 * {@code application.properties} defaults rather than the {@code test}
 * profile's overrides — verifying springdoc stays disabled unless a
 * profile (e.g. {@code dev}) explicitly opts in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:swagger-disabled-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0cy0xMjM0NTY3ODkw",
        "frontend.url=http://localhost:3000",
        "owner.email=admin@example.com",
        "owner.password=TestPassword1"
})
class SwaggerDisabledByDefaultTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void apiDocs_noActiveProfile_returns404() throws Exception {
        mockMvc.perform(get("/api/docs")).andExpect(status().isNotFound());
    }

    @Test
    void swaggerUi_noActiveProfile_returns404() throws Exception {
        mockMvc.perform(get("/api/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
