package com.blog.blog_literario.utils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageValidatorTest {

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46
    };

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] WEBP_BYTES = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00
    };

    @Test
    void validate_validJpeg_doesNotThrow() {
        MockMultipartFile file = new MockMultipartFile("image", "photo.jpg", "image/jpeg", JPEG_BYTES);

        assertThatCode(() -> ImageValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_validPng_doesNotThrow() {
        MockMultipartFile file = new MockMultipartFile("image", "photo.png", "image/png", PNG_BYTES);

        assertThatCode(() -> ImageValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_validWebP_doesNotThrow() {
        MockMultipartFile file = new MockMultipartFile("image", "photo.webp", "image/webp", WEBP_BYTES);

        assertThatCode(() -> ImageValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void validate_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> ImageValidator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se propocionó ningún archivo");
    }

    @Test
    void validate_oversizedFile_throwsIllegalArgumentException() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("image", "big.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> ImageValidator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tamaño máximo permitido 5MB")
                .hasMessageContaining("Tamaño recibido:");
    }

    @Test
    void validate_tooFewBytes_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("image", "tiny.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8});

        assertThatThrownBy(() -> ImageValidator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Archivo demasiado pequeño para ser una imagen valida");
    }

    @Test
    void validate_unrecognizedMagicBytes_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("image", "doc.pdf", "application/pdf",
                new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'});

        assertThatThrownBy(() -> ImageValidator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Formato no es un formato de imagen válido (JPEG, PNG, WebP)");
    }
}
