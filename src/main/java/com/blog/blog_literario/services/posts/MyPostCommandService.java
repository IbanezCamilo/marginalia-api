package com.blog.blog_literario.services.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.posts.CreatePostRequest;
import com.blog.blog_literario.dto.posts.MyPostResponse;
import com.blog.blog_literario.dto.posts.UpdatePostRequest;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.utils.SlugUtils;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class MyPostCommandService {

    private final PostRepository postRepository;

    private final UserRepository userRepository;

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<MyPostResponse> list(Integer userId, Pageable pageable) {
        return postRepository
                .findByAuthor_Id(userId, pageable)
                .map(this::ToResponse);
    }

    public MyPostResponse create(Integer userId, CreatePostRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        Category category = categoryRepository.findById(request.categoryId()).orElseThrow();

        Post post = new Post(
                request.title(),
                request.content(),
                "DRAFT",
                SlugUtils.toSlug(request.title()),
                user,
                category
        );

        postRepository.save(post);
        return ToResponse(post);
    }

    public MyPostResponse update(Integer userId, Integer postId, UpdatePostRequest request) {
        Post post = postRepository
                .findByIdAndAuthor_Id(postId, userId)
                .orElseThrow();
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setStatus(request.status());

        return ToResponse(post);
    }

    public void delete(Integer userId, Integer postId) {
        Post post = postRepository
                .findByIdAndAuthor_Id(postId, userId)
                .orElseThrow();

        postRepository.delete(post);
    }

    public MyPostResponse ToResponse(Post post) {
        return new MyPostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getStatus(),
                post.getCategory().getName(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
