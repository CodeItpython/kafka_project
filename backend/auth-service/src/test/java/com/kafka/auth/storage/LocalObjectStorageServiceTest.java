package com.kafka.auth.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageServiceTest {

    private LocalObjectStorageService service(Path root) {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setRootPath(root.toString());
        return new LocalObjectStorageService(properties);
    }

    @Test
    void loadReturnsStoredObjectForExistingFile(@TempDir Path root) throws Exception {
        Path target = root.resolve("profiles/a.png");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[] {1, 2, 3});

        StoredObject stored = service(root).load("profiles/a.png");

        assertThat(stored).isNotNull();
        assertThat(stored.resource()).isNotNull();
        assertThat(stored.resource().isReadable()).isTrue();
    }

    @Test
    void loadThrowsObjectNotFoundWhenFileMissing(@TempDir Path root) {
        assertThatThrownBy(() -> service(root).load("profiles/missing.png"))
                .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void loadRejectsPathTraversalKey(@TempDir Path root) {
        // StorageKeyValidator.normalize가 경로 탈출 키를 IllegalArgumentException(400)으로 먼저 차단한다.
        assertThatThrownBy(() -> service(root).load("../secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
