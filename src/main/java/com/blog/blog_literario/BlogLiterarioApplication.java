package com.blog.blog_literario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.blog.blog_literario.config.properties")
public class BlogLiterarioApplication {

	public static void main(String[] args) {
		SpringApplication.run(BlogLiterarioApplication.class, args);
	}

}