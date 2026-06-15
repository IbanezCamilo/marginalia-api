package com.blog.blog_literario.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileNameGeneratorTest {

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] WEBP_BYTES = {0x52, 0x49, 0x46, 0x46};

    @Test
    void generate_jpegBytes_returnsUuidWithJpgExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "photo.jpg", "image/jpeg", JPEG_BYTES);

        String result = FileNameGenerator.generate(file);

        assertThat(result).endsWith(".jpg");
        assertThat(result).matches("^[0-9a-fA-F-]{36}\\.jpg$");
    }

    @Test
    void generate_pngBytes_returnsUuidWithPngExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "photo.png", "image/png", PNG_BYTES);

        String result = FileNameGenerator.generate(file);

        assertThat(result).endsWith(".png");
        assertThat(result).matches("^[0-9a-fA-F-]{36}\\.png$");
    }

    @Test
    void generate_webpBytes_returnsUuidWithWebpExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "photo.webp", "image/webp", WEBP_BYTES);

        String result = FileNameGenerator.generate(file);

        assertThat(result).endsWith(".webp");
        assertThat(result).matches("^[0-9a-fA-F-]{36}\\.webp$");
    }

    @Test
    void generate_unrecognizedBytes_throwsIllegalStateException() {
        MockMultipartFile file = new MockMultipartFile("image", "doc.pdf", "application/pdf",
                new byte[] {'%', 'P', 'D', 'F'});

        assertThatThrownBy(() -> FileNameGenerator.generate(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Formato no reconocido. Asegúrate de llamar ImageValidator.validate() primero");
    }

    @Test
    void generate_eachCall_returnsUniqueNames() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "photo.jpg", "image/jpeg", JPEG_BYTES);

        String first = FileNameGenerator.generate(file);
        String second = FileNameGenerator.generate(file);

        assertThat(first).isNotEqualTo(second);
    }
}
