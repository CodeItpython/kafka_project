package com.kafka.auth.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

public class LocalObjectStorageService implements ObjectStorageService {
    private final Path rootPath;

    public LocalObjectStorageService(StorageProperties properties) {
        this.rootPath = Paths.get(properties.getLocal().getRootPath()).toAbsolutePath().normalize();
    }

    @Override
    public void store(String objectKey, MultipartFile file, String contentType) {
        String normalizedKey = StorageKeyValidator.normalize(objectKey);
        try {
            Files.createDirectories(rootPath);
            Path target = rootPath.resolve(normalizedKey).normalize();
            if (!target.startsWith(rootPath)) {
                throw new IllegalArgumentException("파일 경로가 올바르지 않습니다.");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("파일 저장에 실패했습니다.", exception);
        }
    }

    @Override
    public StoredObject load(String objectKey) {
        String normalizedKey = StorageKeyValidator.normalize(objectKey);
        try {
            Path target = rootPath.resolve(normalizedKey).normalize();
            if (!target.startsWith(rootPath) || !Files.exists(target)) {
                throw new IllegalArgumentException("파일을 찾을 수 없습니다.");
            }
            Resource resource = new UrlResource(target.toUri());
            if (!resource.isReadable()) {
                throw new IllegalArgumentException("파일을 읽을 수 없습니다.");
            }
            String contentType = Files.probeContentType(target);
            return new StoredObject(resource, contentType);
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("파일 경로가 올바르지 않습니다.", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("파일을 읽는 중 오류가 발생했습니다.", exception);
        }
    }
}
