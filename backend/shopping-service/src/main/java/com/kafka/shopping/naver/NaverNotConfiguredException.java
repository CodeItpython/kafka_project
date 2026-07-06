package com.kafka.shopping.naver;

/** Thrown when NAVER_CLIENT_ID/SECRET are not set, so live product data is unavailable. */
public class NaverNotConfiguredException extends RuntimeException {
    public NaverNotConfiguredException(String message) {
        super(message);
    }
}
