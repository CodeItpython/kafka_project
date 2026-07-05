package com.kafka.chat.storage;

import org.springframework.core.io.Resource;

public record StoredObject(
        Resource resource,
        String contentType
) {
}
