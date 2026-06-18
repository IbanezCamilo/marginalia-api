package com.blog.blog_literario.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostContentSanitizerTest {

    @Test
    void returnsNullWhenContentIsNull() {
        assertThat(PostContentSanitizer.sanitize(null)).isNull();
    }

    @Test
    void passesThroughValidTiptapDocumentUnchanged() {
        String content = """
                {"type":"doc","content":[{"type":"paragraph","content":[\
                {"type":"text","text":"Hello","marks":[{"type":"bold"}]}]}]}""";

        String sanitized = PostContentSanitizer.sanitize(content);

        assertThat(sanitized).contains("\"type\":\"doc\"");
        assertThat(sanitized).contains("\"text\":\"Hello\"");
        assertThat(sanitized).contains("\"type\":\"bold\"");
    }

    @Test
    void rejectsContentThatIsNotValidJson() {
        assertThatThrownBy(() -> PostContentSanitizer.sanitize("<p>not json</p>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsJsonThatIsNotATiptapDocument() {
        assertThatThrownBy(() -> PostContentSanitizer.sanitize("{\"type\":\"paragraph\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stripsDisallowedNodeTypes() {
        String content = """
                {"type":"doc","content":[\
                {"type":"script","content":[]},\
                {"type":"paragraph","content":[{"type":"text","text":"Safe"}]}]}""";

        String sanitized = PostContentSanitizer.sanitize(content);

        assertThat(sanitized).doesNotContain("\"type\":\"script\"");
        assertThat(sanitized).contains("\"text\":\"Safe\"");
    }

    @Test
    void stripsDisallowedMarkTypes() {
        String content = """
                {"type":"doc","content":[{"type":"paragraph","content":[\
                {"type":"text","text":"Hi","marks":[{"type":"bold"},{"type":"highlight"}]}]}]}""";

        String sanitized = PostContentSanitizer.sanitize(content);

        assertThat(sanitized).contains("\"type\":\"bold\"");
        assertThat(sanitized).doesNotContain("highlight");
    }

    @Test
    void stripsLinkMarksWithJavascriptHref() {
        String content = """
                {"type":"doc","content":[{"type":"paragraph","content":[\
                {"type":"text","text":"click","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]}]}]}""";

        String sanitized = PostContentSanitizer.sanitize(content);

        assertThat(sanitized).doesNotContain("javascript:");
        assertThat(sanitized).doesNotContain("\"type\":\"link\"");
    }

    @Test
    void keepsLinkMarksWithSafeHref() {
        String content = """
                {"type":"doc","content":[{"type":"paragraph","content":[\
                {"type":"text","text":"click","marks":[{"type":"link","attrs":{"href":"https://example.com"}}]}]}]}""";

        String sanitized = PostContentSanitizer.sanitize(content);

        assertThat(sanitized).contains("\"type\":\"link\"");
        assertThat(sanitized).contains("https://example.com");
    }

    @Test
    void stripsDisallowedTextAlignValue() {
        String content = """
                {"type":"doc","content":[\
                {"type":"paragraph","attrs":{"textAlign":"evil"},"content":[{"type":"text","text":"Hi"}]}]}""";

        String sanitized = PostContentSanitizer.sanitize(content);

        assertThat(sanitized).doesNotContain("textAlign");
    }

    @Test
    void keepsAllowedTextAlignValue() {
        String content = """
                {"type":"doc","content":[\
                {"type":"paragraph","attrs":{"textAlign":"center"},"content":[{"type":"text","text":"Hi"}]}]}""";

        String sanitized = PostContentSanitizer.sanitize(content);

        assertThat(sanitized).contains("\"textAlign\":\"center\"");
    }
}
