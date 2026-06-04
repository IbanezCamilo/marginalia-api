package com.blog.blog_literario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan("com.blog.blog_literario.config.properties")
public class BlogLiterarioApplication {

	public static void main(String[] args) {
		SpringApplication.run(BlogLiterarioApplication.class, args);
	}

}