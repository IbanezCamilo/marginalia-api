package com.blog.blog_literario.services.images;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.blog.blog_literario.config.properties.R2Properties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] WEBP_BYTES =
            {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};

    @Mock
    private S3Client r2Client;

    private R2StorageService storageService;

    @BeforeEach
    void setUp() {
        R2Properties props = new R2Properties(
                "account-id", "access-key", "secret-key",
                "marginalia-media", "https://assets.example.com");
        storageService = new R2StorageService(r2Client, props);
    }

    @Test
    void save_validImage_uploadsAndReturnsGeneratedName() {
        MockMultipartFile file = new MockMultipartFile("image", "photo.jpg", "image/jpeg", JPEG_BYTES);

        String fileName = storageService.save(file, null);

        assertThat(fileName).endsWith(".jpg");
        verify(r2Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void save_ignoresClientSuppliedContentType_andStoresTypeDerivedFromMagicBytes() {
        // R2 objects are served from a public custom domain, outside this app's
        // SecurityFilterChain — so no nosniff/CSP header applies to them. Storing the
        // client's Content-Type would let an author upload valid JPEG bytes declared as
        // text/html and have Cloudflare serve it as HTML: stored XSS on the media domain.
        MockMultipartFile file = new MockMultipartFile("image", "payload.jpg", "text/html", JPEG_BYTES);

        storageService.save(file, null);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void save_nullClientContentType_stillStoresDetectedContentType() {
        MockMultipartFile file = new MockMultipartFile("image", "photo.png", null, PNG_BYTES);

        storageService.save(file, null);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void save_webpImage_storesWebpContentTypeAndExtension() {
        MockMultipartFile file = new MockMultipartFile("image", "photo.webp", "image/webp", WEBP_BYTES);

        String fileName = storageService.save(file, null);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(r2Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("image/webp");
        assertThat(fileName).endsWith(".webp");
    }

    @Test
    void save_withPreviousFile_deletesOldFileAfterSuccessfulUpload() {
        MockMultipartFile file = new MockMultipartFile("image", "new.jpg", "image/jpeg", JPEG_BYTES);

        storageService.save(file, "old.jpg");

        verify(r2Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(r2Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void save_uploadFails_throwsAndDoesNotDeletePreviousFile() {
        MockMultipartFile file = new MockMultipartFile("image", "new.jpg", "image/jpeg", JPEG_BYTES);
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storageService.save(file, "old.jpg"))
                .isInstanceOf(RuntimeException.class);

        verify(r2Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void save_invalidImage_throwsIllegalArgumentException_andDoesNotUpload() {
        MockMultipartFile file = new MockMultipartFile("image", "doc.pdf", "application/pdf",
                new byte[] {'%', 'P', 'D', 'F'});

        assertThatThrownBy(() -> storageService.save(file, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(r2Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void delete_nonExistentKey_doesNotThrow() {
        when(r2Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("no such key").build());

        assertThatCode(() -> storageService.delete("missing.jpg")).doesNotThrowAnyException();
    }

    @Test
    void delete_blankOrNullFileName_noOp() {
        assertThatCode(() -> storageService.delete(null)).doesNotThrowAnyException();
        assertThatCode(() -> storageService.delete("")).doesNotThrowAnyException();
        assertThatCode(() -> storageService.delete("   ")).doesNotThrowAnyException();

        verify(r2Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void buildUrl_validFileName_returnsPublicBaseUrlPlusFileName() {
        assertThat(storageService.buildUrl("photo.jpg"))
                .isEqualTo("https://assets.example.com/photo.jpg");
    }

    @Test
    void buildUrl_nullOrBlankFileName_returnsNull() {
        assertThat(storageService.buildUrl(null)).isNull();
        assertThat(storageService.buildUrl("")).isNull();
        assertThat(storageService.buildUrl("   ")).isNull();
    }
}
