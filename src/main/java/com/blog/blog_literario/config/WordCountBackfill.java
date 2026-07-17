package com.blog.blog_literario.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.utils.PostPlainText;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-time backfill of {@code word_count} for posts created before migration V7.
 *
 * <p>Uses the exact same extraction as the write path ({@link PostPlainText}) so
 * backfilled rows and newly saved rows can never disagree. Idempotent: only rows
 * with a NULL word_count are visited, so after the first successful run this is a no-op.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WordCountBackfill implements CommandLineRunner {

    private final PostRepository postRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<Post> pending = postRepository.findByWordCountIsNull();
        if (pending.isEmpty()) {
            log.debug("WordCountBackfill: nothing to backfill");
            return;
        }
        for (Post post : pending) {
            post.setWordCount(PostPlainText.countWords(PostPlainText.extractPlainText(post.getContent())));
        }
        postRepository.saveAll(pending);
        log.info("WordCountBackfill: computed word_count for {} posts", pending.size());
    }
}
