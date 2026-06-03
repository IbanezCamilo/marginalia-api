package com.blog.blog_literario.support;

import com.blog.blog_literario.config.properties.FrontendProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@EnableConfigurationProperties(FrontendProperties.class)
public class WebMvcTestConfig {}
