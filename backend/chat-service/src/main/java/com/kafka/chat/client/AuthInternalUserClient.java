package com.kafka.chat.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for auth-service's internal user directory. Requests carry the
 * shared internal-API token (added by {@link InternalApiTokenInterceptor}); the
 * base URL is resolved from {@code app.services.auth-service.url}.
 */
@FeignClient(
        name = "auth-user-directory",
        url = "${app.services.auth-service.url:http://localhost:8890}",
        path = "/api/internal/users"
)
public interface AuthInternalUserClient {

    @GetMapping
    List<UserView> byEmails(@RequestParam("emails") List<String> emails);

    @GetMapping("/search")
    List<UserView> search(@RequestParam(value = "query", required = false) String query,
                          @RequestParam("limit") int limit);

    @GetMapping("/all")
    List<UserView> all();
}
