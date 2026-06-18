package com.blog.blog_literario.utils;

import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Strips unsafe nodes/marks from author-submitted post content before it is persisted.
 *
 * <p>{@code content} is a TipTap/ProseMirror JSON document, not HTML. This walks the
 * document tree and keeps only the node and mark types the editor actually produces,
 * dropping anything else, and restricts link hrefs to a safe set of URL schemes — so
 * stored content can never carry an executable payload (e.g. {@code javascript:} links)
 * when it's later rendered for readers and moderators.
 */
public final class PostContentSanitizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> ALLOWED_NODE_TYPES = Set.of(
            "doc", "paragraph", "text", "heading", "bulletList", "orderedList",
            "listItem", "blockquote", "hardBreak", "horizontalRule"
    );

    private static final Set<String> ALLOWED_MARK_TYPES = Set.of(
            "bold", "italic", "strike", "underline", "link"
    );

    private static final Set<String> ALLOWED_LINK_SCHEMES = Set.of("http", "https", "mailto");

    private static final Set<String> ALLOWED_TEXT_ALIGN = Set.of("left", "center", "right", "justify");

    private PostContentSanitizer() {
    }

    /**
     * Parses {@code content} as a TipTap JSON document, removes any node/mark type or
     * link href the editor doesn't produce, and returns the cleaned document
     * re-serialized as a JSON string.
     *
     * @throws IllegalArgumentException if {@code content} is not a valid TipTap document
     */
    public static String sanitize(String content) {
        if (content == null) {
            return null;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El contenido del post no es un documento JSON válido.");
        }

        if (!(root instanceof ObjectNode rootObj) || !"doc".equals(rootObj.path("type").asText(null))) {
            throw new IllegalArgumentException("El contenido del post no tiene el formato esperado.");
        }

        sanitizeNode(rootObj);

        try {
            return MAPPER.writeValueAsString(rootObj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo procesar el contenido del post.", e);
        }
    }

    private static void sanitizeNode(ObjectNode node) {
        sanitizeMarks(node);
        sanitizeAttrs(node);

        if (node.get("content") instanceof ArrayNode children) {
            Iterator<JsonNode> it = children.iterator();
            while (it.hasNext()) {
                JsonNode child = it.next();
                if (!(child instanceof ObjectNode childObj) || !ALLOWED_NODE_TYPES.contains(childObj.path("type").asText(""))) {
                    it.remove();
                    continue;
                }
                sanitizeNode(childObj);
            }
        }
    }

    private static void sanitizeMarks(ObjectNode node) {
        if (!(node.get("marks") instanceof ArrayNode marks)) {
            return;
        }

        Iterator<JsonNode> it = marks.iterator();
        while (it.hasNext()) {
            JsonNode mark = it.next();
            if (!(mark instanceof ObjectNode markObj) || !ALLOWED_MARK_TYPES.contains(markObj.path("type").asText(""))) {
                it.remove();
                continue;
            }
            if ("link".equals(markObj.path("type").asText())
                    && !hasAllowedScheme(markObj.path("attrs").path("href").asText(""))) {
                it.remove();
            }
        }
    }

    private static void sanitizeAttrs(ObjectNode node) {
        if (!(node.get("attrs") instanceof ObjectNode attrs)) {
            return;
        }

        JsonNode textAlign = attrs.get("textAlign");
        if (textAlign != null && !ALLOWED_TEXT_ALIGN.contains(textAlign.asText(""))) {
            attrs.remove("textAlign");
        }
    }

    private static boolean hasAllowedScheme(String href) {
        int colonIndex = href.indexOf(':');
        if (colonIndex < 0) {
            // Relative/scheme-less URLs (e.g. "/about", "#section") are safe.
            return true;
        }
        return ALLOWED_LINK_SCHEMES.contains(href.substring(0, colonIndex).toLowerCase());
    }
}
