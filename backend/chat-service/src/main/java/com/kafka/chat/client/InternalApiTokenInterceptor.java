package com.kafka.chat.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adds the shared internal-API token header to every outbound Feign request so
 * auth-service can distinguish trusted service-to-service calls on /api/internal
 * from external traffic.
 */
@Component
public class InternalApiTokenInterceptor implements RequestInterceptor {
    public static final String HEADER = "X-Internal-Api-Token";

    private final String token;

    public InternalApiTokenInterceptor(@Value("${app.internal.api-token}") String token) {
        this.token = token;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(HEADER, token);
    }
}
