package com.dentalclinic.service.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.service.config.DentalServiceImageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DentalServiceImageStorageService {

    private static final Pattern STORED_FILENAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$"
    );
    private static final Map<ImageType, String> EXTENSIONS = Map.of(
            ImageType.JPEG, ".jpg",
            ImageType.PNG, ".png",
            ImageType.WEBP, ".webp"
    );
    private static final Map<ImageType, MediaType> MEDIA_TYPES = Map.of(
            ImageType.JPEG, MediaType.IMAGE_JPEG,
            ImageType.PNG, MediaType.IMAGE_PNG,
            ImageType.WEBP, MediaType.parseMediaType("image/webp")
    );

    private final Path storageDirectory;
    private final long maxFileSizeBytes;

    public DentalServiceImageStorageService(DentalServiceImageProperties properties) {
        this.storageDirectory = Path.of(properties.storageDirectory()).toAbsolutePath().normalize();
        this.maxFileSizeBytes = properties.maxFileSizeBytes();
    }

    @PostConstruct
    void initializeStorage() {
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu ảnh dịch vụ", exception);
        }
    }

    public String store(MultipartFile file) {
        validateBasicFile(file);
        ImageType imageType = detectImageType(file);
        validateDeclaredContentType(file, imageType);

        String filename = UUID.randomUUID() + EXTENSIONS.get(imageType);
        Path target = storageDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(storageDirectory)) {
            throw invalidImage("Tên file ảnh không hợp lệ");
        }

        boolean targetCreated = false;
        try (InputStream inputStream = file.getInputStream();
             OutputStream outputStream = Files.newOutputStream(
                     target,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE
             )) {
            targetCreated = true;
            inputStream.transferTo(outputStream);
            return filename;
        } catch (IOException exception) {
            if (targetCreated) {
                tryDeletePartialFile(target);
            }
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_FAILED);
        }
    }

    public StoredImage load(String filename) {
        if (filename == null || !STORED_FILENAME.matcher(filename.toLowerCase(Locale.ROOT)).matches()) {
            throw new NotFoundException("Không tìm thấy ảnh dịch vụ");
        }

        Path imagePath = storageDirectory.resolve(filename).normalize();
        if (!imagePath.getParent().equals(storageDirectory)
                || !Files.isRegularFile(imagePath)
                || !Files.isReadable(imagePath)) {
            throw new NotFoundException("Không tìm thấy ảnh dịch vụ");
        }

        ImageType imageType = imageTypeFromFilename(filename);
        return new StoredImage(new FileSystemResource(imagePath), MEDIA_TYPES.get(imageType));
    }

    private void validateBasicFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidImage("Vui lòng chọn một file ảnh");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.IMAGE_FILE_TOO_LARGE);
        }
    }

    private ImageType detectImageType(MultipartFile file) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(12);
        } catch (IOException exception) {
            throw invalidImage("Không thể đọc file ảnh");
        }

        if (isJpeg(header)) return ImageType.JPEG;
        if (isPng(header)) return ImageType.PNG;
        if (isWebp(header)) return ImageType.WEBP;
        throw invalidImage("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP hợp lệ");
    }

    private void validateDeclaredContentType(MultipartFile file, ImageType imageType) {
        String declaredContentType = file.getContentType();
        if (declaredContentType == null
                || !MEDIA_TYPES.get(imageType).toString().equalsIgnoreCase(declaredContentType.trim())) {
            throw invalidImage("Định dạng file không khớp với nội dung ảnh");
        }
    }

    private ImageType imageTypeFromFilename(String filename) {
        String normalized = filename.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jpg")) return ImageType.JPEG;
        if (normalized.endsWith(".png")) return ImageType.PNG;
        if (normalized.endsWith(".webp")) return ImageType.WEBP;
        throw new NotFoundException("Không tìm thấy ảnh dịch vụ");
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && unsigned(header[0]) == 0xFF
                && unsigned(header[1]) == 0xD8
                && unsigned(header[2]) == 0xFF;
    }

    private boolean isPng(byte[] header) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (header.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (unsigned(header[index]) != signature[index]) return false;
        }
        return true;
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private BusinessException invalidImage(String message) {
        return new BusinessException(ErrorCode.INVALID_IMAGE_FILE, message);
    }

    private void tryDeletePartialFile(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // The original storage error is the actionable failure for the request.
        }
    }

    private enum ImageType {
        JPEG,
        PNG,
        WEBP
    }

    public record StoredImage(Resource resource, MediaType mediaType) {
    }
}
