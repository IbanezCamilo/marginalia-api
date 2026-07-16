package com.blog.blog_literario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.repositories.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WordCountBackfillTest {

    @Mock PostRepository postRepository;
    @InjectMocks WordCountBackfill backfill;

    @Test
    void run_computesWordCountForPendingRowsUsingTheSharedExtraction() throws Exception {
        Post pending = new Post("Titulo cualquiera",
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":"
                + "[{\"type\":\"text\",\"text\":\"uno dos tres\"}]}]}",
                PostStatus.PUBLISHED, "titulo-cualquiera", null, null);
        given(postRepository.findByWordCountIsNull()).willReturn(List.of(pending));

        backfill.run();

        assertThat(pending.getWordCount()).isEqualTo(3);
        verify(postRepository).saveAll(List.of(pending));
    }

    @Test
    void run_nothingPending_savesNothing() throws Exception {
        given(postRepository.findByWordCountIsNull()).willReturn(List.of());

        backfill.run();

        verify(postRepository, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
