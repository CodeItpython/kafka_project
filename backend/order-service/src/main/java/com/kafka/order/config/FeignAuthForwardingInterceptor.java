package com.kafka.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the caller's {@code Authorization: Bearer <jwt>} header onto outbound
 * Feign calls. shopping-service scopes the cart by the JWT subject and has no
 * service-to-service token, so order-service must present the end user's own token
 * to read/clear that user's cart. Runs on the request thread, so the current
 * request's header is available via RequestContextHolder.
 */
@Component
public class FeignAuthForwardingInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey("Authorization")) {
            return;
        }
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        String authorization = attributes.getRequest().getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            template.header("Authorization", authorization);
        }
    }
}
