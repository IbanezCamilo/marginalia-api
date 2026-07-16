package com.blog.blog_literario.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extracts the readable plain text of a TipTap/ProseMirror JSON document.
 *
 * <p>Mirrors the frontend's {@code editorContentToText} (editorContent.js): text nodes
 * joined with spaces, whitespace collapsed, trimmed. This single traversal is the source
 * for {@code word_count} today and is where a future persisted searchable-text column
 * must be produced too — one walk, all derivations.
 */
public final class PostPlainText {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PostPlainText() {
    }

    /** Never returns null; unparseable or blank content yields the empty string. */
    public static String extractPlainText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (JsonProcessingException e) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        walk(root, sb);
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    public static int countWords(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return 0;
        }
        return plainText.trim().split("\\s+").length;
    }

    private static void walk(JsonNode node, StringBuilder sb) {
        if (node.hasNonNull("text")) {
            sb.append(node.get("text").asText()).append(' ');
        }
        JsonNode children = node.get("content");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                walk(child, sb);
            }
        }
    }
}
