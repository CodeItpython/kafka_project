package com.kafka.auth.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
public class S3ObjectStorageService implements ObjectStorageService {
    private final S3Client s3Client;
    private final String bucket;

    public S3ObjectStorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.getS3().getBucket();
    }

    @Override
    public void store(String objectKey, MultipartFile file, String contentType) {
        String normalizedKey = StorageKeyValidator.normalize(objectKey);
        ensureBucket();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizedKey)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new UncheckedIOException("파일 저장에 실패했습니다.", exception);
        }
    }

    @Override
    public StoredObject load(String objectKey) {
        String normalizedKey = StorageKeyValidator.normalize(objectKey);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(normalizedKey)
                .build();
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return new StoredObject(
                    new ByteArrayResource(response.asByteArray()),
                    response.response().contentType()
            );
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException exception) {
            log.warn("Object not found in S3. bucket={}, key={}", bucket, normalizedKey);
            return null;
        }
    }

    private void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException exception) {
            createBucket();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                createBucket();
                return;
            }
            throw exception;
        }
    }

    private void createBucket() {
        log.info("Creating object storage bucket. bucket={}", bucket);
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    }
}
