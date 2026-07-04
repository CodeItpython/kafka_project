package com.kafka.auth.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3ObjectStorageService service() {
        return new S3ObjectStorageService(s3Client, new StorageProperties());
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadReturnsStoredObjectWithContentType() {
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(new byte[] {1, 2, 3});
        when(responseBytes.response()).thenReturn(GetObjectResponse.builder().contentType("image/png").build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        StoredObject stored = service().load("chat/a.png");

        assertThat(stored).isNotNull();
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.resource()).isNotNull();
    }

    @Test
    void loadThrowsObjectNotFoundWhenKeyMissing() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThatThrownBy(() -> service().load("chat/missing.png"))
                .isInstanceOf(ObjectNotFoundException.class);
    }
}
