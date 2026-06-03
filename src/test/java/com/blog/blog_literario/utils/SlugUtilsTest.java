package com.blog.blog_literario.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugUtilsTest {

    @Test
    void toSlug_basicTitle_producesHyphenated() {
        assertThat(SlugUtils.toSlug("Hello World")).isEqualTo("hello-world");
    }

    @Test
    void toSlug_spanishAccents_stripped() {
        assertThat(SlugUtils.toSlug("Canción de Amor")).isEqualTo("cancion-de-amor");
    }

    @Test
    void toSlug_specialChars_removed() {
        assertThat(SlugUtils.toSlug("C++ is great!")).isEqualTo("c-is-great");
    }

    @Test
    void toSlug_multipleSpaces_collapsedToOneHyphen() {
        assertThat(SlugUtils.toSlug("a   b")).isEqualTo("a-b");
    }

    @Test
    void toSlug_leadingTrailingHyphens_stripped() {
        assertThat(SlugUtils.toSlug("-leading trailing-")).isEqualTo("leading-trailing");
    }

    @Test
    void toSlug_doubleHyphens_collapsedToSingle() {
        assertThat(SlugUtils.toSlug("hello--world")).isEqualTo("hello-world");
    }

    @Test
    void toSlug_upperCase_convertedToLower() {
        assertThat(SlugUtils.toSlug("SPRING BOOT")).isEqualTo("spring-boot");
    }

    @Test
    void toSlug_mixedAccentsAndSpaces_normalizedCorrectly() {
        assertThat(SlugUtils.toSlug("El Niño está aquí")).isEqualTo("el-nino-esta-aqui");
    }
}
