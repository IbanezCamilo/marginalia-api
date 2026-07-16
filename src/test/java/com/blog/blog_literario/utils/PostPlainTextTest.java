package com.blog.blog_literario.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostPlainTextTest {

    private static final String DOC = """
            {"type":"doc","content":[
              {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"El otoño"}]},
              {"type":"paragraph","content":[
                {"type":"text","text":"Las hojas caen "},
                {"type":"text","marks":[{"type":"bold"}],"text":"lentamente"}
              ]},
              {"type":"paragraph"}
            ]}
            """;

    @Test
    void extractPlainText_walksTextNodesJoiningWithSpaces() {
        assertThat(PostPlainText.extractPlainText(DOC))
                .isEqualTo("El otoño Las hojas caen lentamente");
    }

    @Test
    void extractPlainText_nullOrBlank_returnsEmptyString() {
        assertThat(PostPlainText.extractPlainText(null)).isEmpty();
        assertThat(PostPlainText.extractPlainText("  ")).isEmpty();
    }

    @Test
    void extractPlainText_invalidJson_returnsEmptyStringInsteadOfThrowing() {
        assertThat(PostPlainText.extractPlainText("not json")).isEmpty();
    }

    @Test
    void countWords_splitsOnWhitespace() {
        assertThat(PostPlainText.countWords("El otoño Las hojas caen lentamente")).isEqualTo(6);
    }

    @Test
    void countWords_emptyOrNull_isZero() {
        assertThat(PostPlainText.countWords("")).isZero();
        assertThat(PostPlainText.countWords(null)).isZero();
        assertThat(PostPlainText.countWords("   ")).isZero();
    }
}
