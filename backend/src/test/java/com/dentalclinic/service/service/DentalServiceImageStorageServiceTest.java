package com.dentalclinic.service.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.service.config.DentalServiceImageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DentalServiceImageStorageServiceTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @TempDir
    Path temporaryDirectory;

    private DentalServiceImageStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new DentalServiceImageStorageService(
                new DentalServiceImageProperties(temporaryDirectory.toString(), 5 * 1024 * 1024)
        );
        storageService.initializeStorage();
    }

    @Test
    void shouldStoreAndLoadValidPngWithGeneratedFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "service.png", MediaType.IMAGE_PNG_VALUE, PNG_BYTES
        );

        String filename = storageService.store(file);
        DentalServiceImageStorageService.StoredImage storedImage = storageService.load(filename);

        assertThat(filename).matches("[0-9a-f-]{36}\\.png");
        assertThat(storedImage.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(storedImage.resource().getContentAsByteArray()).isEqualTo(PNG_BYTES);
        assertThat(Files.isRegularFile(temporaryDirectory.resolve(filename))).isTrue();
    }

    @Test
    void shouldRejectEmptyUnsupportedAndSpoofedFiles() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.png", MediaType.IMAGE_PNG_VALUE, new byte[0]
        );
        MockMultipartFile gif = new MockMultipartFile(
                "file", "service.gif", MediaType.IMAGE_GIF_VALUE, "GIF89a".getBytes()
        );
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "service.jpg", MediaType.IMAGE_JPEG_VALUE, PNG_BYTES
        );

        assertInvalidImage(empty);
        assertInvalidImage(gif);
        assertInvalidImage(spoofed);
    }

    @Test
    void shouldRejectFileLargerThanConfiguredLimit() {
        DentalServiceImageStorageService smallLimitStorage = new DentalServiceImageStorageService(
                new DentalServiceImageProperties(temporaryDirectory.toString(), PNG_BYTES.length - 1L)
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "service.png", MediaType.IMAGE_PNG_VALUE, PNG_BYTES
        );

        assertThatThrownBy(() -> smallLimitStorage.store(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_FILE_TOO_LARGE));
    }

    @Test
    void shouldRejectUnknownOrUnsafeStoredFilename() {
        assertThatThrownBy(() -> storageService.load("../service.png"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> storageService.load("00000000-0000-0000-0000-000000000000.png"))
                .isInstanceOf(NotFoundException.class);
    }

    private void assertInvalidImage(MockMultipartFile file) {
        assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
    }
}
