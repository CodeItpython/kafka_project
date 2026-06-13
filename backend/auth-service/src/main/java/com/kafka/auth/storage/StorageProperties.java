package com.kafka.auth.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private StorageType type = StorageType.LOCAL;
    private Local local = new Local();
    private S3 s3 = new S3();

    public enum StorageType {
        LOCAL,
        S3
    }

    @Getter
    @Setter
    public static class Local {
        private String rootPath = "uploads";
    }

    @Getter
    @Setter
    public static class S3 {
        private String endpoint = "";
        private String region = "us-east-1";
        private String bucket = "kafka-talk-files";
        private String accessKey = "kafka-talk";
        private String secretKey = "kafka-talk-secret";
        private boolean pathStyleAccess = true;
    }
}
