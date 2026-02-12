package com.blog.blog_literario.controllers.posts;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import org.springframework.security.core.Authentication;

@RequestMapping("api/me/posts")
public class MyPostController {

    @GetMapping
    public List<MyPostResponse> list(Authentication authentication) {
    }
}
