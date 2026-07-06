package com.kafka.auth.internal;

import com.kafka.auth.repository.UserAccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal user directory consumed by chat-service over REST. Guarded by
 * {@link com.kafka.auth.config.InternalApiTokenFilter}; not for public traffic.
 */
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class InternalUserController {
    private static final int MAX_SEARCH_LIMIT = 50;

    private final UserAccountRepository userAccountRepository;

    @GetMapping
    public List<InternalUserResponse> byEmails(@RequestParam("emails") List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        return userAccountRepository.findByEmailIn(emails)
                .stream()
                .map(InternalUserResponse::from)
                .toList();
    }

    @GetMapping("/search")
    public List<InternalUserResponse> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "30") int limit
    ) {
        // limit is honored by the caller's paging; the repository methods cap at 30 by name.
        List<InternalUserResponse> results = (query == null || query.isBlank()
                ? userAccountRepository.findTop30ByOrderByNameAsc()
                : userAccountRepository.findTop30ByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByNameAsc(query, query))
                .stream()
                .map(InternalUserResponse::from)
                .toList();
        int cap = Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        return results.size() > cap ? results.subList(0, cap) : results;
    }

    @GetMapping("/all")
    public List<InternalUserResponse> all() {
        return userAccountRepository.findAll()
                .stream()
                .map(InternalUserResponse::from)
                .toList();
    }
}
