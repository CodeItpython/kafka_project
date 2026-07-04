package com.kafka.auth.storage;

/**
 * 저장소에서 요청한 객체를 찾을 수 없을 때 발생한다.
 * 구현체(S3/로컬)와 무관하게 동일하게 던져지며, ApiExceptionHandler에서 404로 매핑된다.
 */
public class ObjectNotFoundException extends RuntimeException {
    public ObjectNotFoundException(String message) {
        super(message);
    }
}
