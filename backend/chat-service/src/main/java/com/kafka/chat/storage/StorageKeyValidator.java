package com.kafka.chat.storage;

final class StorageKeyValidator {
    private StorageKeyValidator() {
    }

    static String normalize(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("파일 경로가 올바르지 않습니다.");
        }
        String normalized = objectKey.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains("/..") || normalized.equals("..")) {
            throw new IllegalArgumentException("파일 경로가 올바르지 않습니다.");
        }
        return normalized;
    }
}
