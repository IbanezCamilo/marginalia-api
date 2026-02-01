package com.blog.blog_literario.services.general;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.posts.postRequestDTO;
import com.blog.blog_literario.dto.posts.postUpdateDTO;
import com.blog.blog_literario.model.Post;

public interface PostService {
    Post createPost(postRequestDTO dto, MultipartFile image); 
    Post updatePost(Integer id, postUpdateDTO dto); 
    Post getPostById(Integer id);
    List<Post> getAllPosts(); 
}
