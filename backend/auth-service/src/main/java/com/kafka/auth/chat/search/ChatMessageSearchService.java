package com.kafka.auth.chat.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageSearchService {
    private static final int MAX_RESULT_SIZE = 50;
    private static final List<String> SEARCH_AS_YOU_TYPE_FIELDS = List.of(
            "content^3",
            "content._2gram^2",
            "content._3gram^2",
            "content._index_prefix^4",
            "roomName^2",
            "roomName._2gram",
            "roomName._3gram",
            "roomName._index_prefix^3",
            "senderName",
            "senderName._2gram",
            "senderName._3gram",
            "senderName._index_prefix^2"
    );
    private static final List<String> EXACT_FIELDS = List.of(
            "content^2",
            "roomName^2",
            "senderName"
    );

    private final ElasticsearchOperations elasticsearchOperations;
    private final ChatMessageSearchRepository fallbackRepository;

    public List<ChatMessageSearchDocument> search(String query, int size) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return execute(normalizedQuery, normalizeSize(size));
    }

    public List<ChatMessageSearchDocument> suggestions(String query, int size) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return execute(normalizedQuery, normalizeSize(size));
    }

    private List<ChatMessageSearchDocument> execute(String query, int size) {
        try {
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(root -> root.bool(bool -> bool
                            .should(should -> should.multiMatch(multiMatch -> multiMatch
                                    .query(query)
                                    .type(TextQueryType.BoolPrefix)
                                    .fields(SEARCH_AS_YOU_TYPE_FIELDS)
                            ))
                            .should(should -> should.multiMatch(multiMatch -> multiMatch
                                    .query(query)
                                    .fields(EXACT_FIELDS)
                            ))
                            .minimumShouldMatch("1")
                    ))
                    .withSort(sort -> sort.field(field -> field.field("createdAt").order(SortOrder.Desc)))
                    .withPageable(PageRequest.of(0, size))
                    .build();

            return elasticsearchOperations.search(nativeQuery, ChatMessageSearchDocument.class)
                    .stream()
                    .map(SearchHit::getContent)
                    .toList();
        } catch (RuntimeException exception) {
            return fallbackRepository
                    .findTop20ByContentContainingOrRoomNameContainingOrderByCreatedAtDesc(query, query)
                    .stream()
                    .limit(size)
                    .toList();
        }
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_RESULT_SIZE);
    }

    private String normalize(String query) {
        return query == null ? "" : query.trim();
    }
}
