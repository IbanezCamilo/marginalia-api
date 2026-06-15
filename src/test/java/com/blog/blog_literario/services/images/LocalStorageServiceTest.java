package com.blog.blog_literario.services.images;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.blog.blog_literario.config.properties.AppProperties;

class LocalStorageServiceTest {

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @TempDir
    Path tempDir;

    private LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(new AppProperties("http://localhost:8080"));
        ReflectionTestUtils.setField(storageService, "uploadDir", tempDir.toString());
    }

    @Test
    void save_validImage_writesFileAndReturnsGeneratedName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "photo.jpg", "image/jpeg", JPEG_BYTES);

        String fileName = storageService.save(file, null);

        assertThat(fileName).endsWith(".jpg");
        assertThat(Files.exists(tempDir.resolve(fileName))).isTrue();
    }

    @Test
    void save_withPreviousFile_deletesOldFileBeforeSavingNew() throws Exception {
        MockMultipartFile previousUpload = new MockMultipartFile("image", "old.jpg", "image/jpeg", JPEG_BYTES);
        String previousFileName = storageService.save(previousUpload, null);
        assertThat(Files.exists(tempDir.resolve(previousFileName))).isTrue();

        MockMultipartFile newUpload = new MockMultipartFile("image", "new.jpg", "image/jpeg", JPEG_BYTES);
        String newFileName = storageService.save(newUpload, previousFileName);

        assertThat(Files.exists(tempDir.resolve(previousFileName))).isFalse();
        assertThat(Files.exists(tempDir.resolve(newFileName))).isTrue();
    }

    @Test
    void save_invalidImage_throwsIllegalArgumentException_andDoesNotWriteFile() {
        MockMultipartFile file = new MockMultipartFile("image", "doc.pdf", "application/pdf",
                new byte[] {'%', 'P', 'D', 'F'});

        assertThatThrownBy(() -> storageService.save(file, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(tempDir.toFile().listFiles()).isEmpty();
    }

    @Test
    void delete_existingFile_removesFromDisk() throws Exception {
        Path file = tempDir.resolve("existing.jpg");
        Files.write(file, JPEG_BYTES);

        storageService.delete("existing.jpg");

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void delete_nonExistentFile_doesNotThrow() {
        assertThatCode(() -> storageService.delete("missing.jpg")).doesNotThrowAnyException();
    }

    @Test
    void delete_blankOrNullFileName_noOp() {
        assertThatCode(() -> storageService.delete(null)).doesNotThrowAnyException();
        assertThatCode(() -> storageService.delete("")).doesNotThrowAnyException();
        assertThatCode(() -> storageService.delete("   ")).doesNotThrowAnyException();
    }

    @Test
    void delete_pathTraversalAttempt_logsWarningAndDoesNotDeleteOutsideFile() throws Exception {
        Path outsideFile = tempDir.resolveSibling("outside-secret.txt");
        Files.write(outsideFile, "secret".getBytes());

        try {
            storageService.delete("../outside-secret.txt");

            assertThat(Files.exists(outsideFile)).isTrue();
        } finally {
            Files.deleteIfExists(outsideFile);
        }
    }

    @Test
    void buildUrl_nullOrBlankFileName_returnsNull() {
        assertThat(storageService.buildUrl(null)).isNull();
        assertThat(storageService.buildUrl("")).isNull();
        assertThat(storageService.buildUrl("   ")).isNull();
    }

    @Test
    void buildUrl_validFileName_returnsBaseUrlPlusImagesPath() {
        String result = storageService.buildUrl("photo.jpg");

        assertThat(result).isEqualTo("http://localhost:8080/api/images/photo.jpg");
    }
}
