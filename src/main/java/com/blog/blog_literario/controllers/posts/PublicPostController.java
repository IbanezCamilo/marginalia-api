package com.blog.blog_literario.controllers.posts;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.services.general.PostService;

@RestController
@RequestMapping("/api/public/posts")
public class PublicPostController {

    @Autowired
    private PostService postService;

    @GetMapping // Método para obtener todos los posts
    public ResponseEntity<?> getAllPosts() {
        List<Post> posts = postService.getAllPosts();
        return ResponseEntity.ok(posts); // status 200 = OK
    }

    @GetMapping("/{id}") // Método para obtener un post por ID
    public ResponseEntity<?> getPostById(@PathVariable Integer id) {
        Post post = postService.getPostById(id);
        return ResponseEntity.ok(post); // status 200 = OK
    }
}
