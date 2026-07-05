package com.kafka.chat.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ObjectStorageService {
    void store(String objectKey, MultipartFile file, String contentType);

    StoredObject load(String objectKey);
}
