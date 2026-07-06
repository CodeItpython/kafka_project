package com.kafka.shopping.search;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * One document per user search. Powers the popular-keyword ranking (terms over the last N
 * hours) and Kibana analytics of what users search for. {@code keyword} is the normalized
 * (lowercased, whitespace-collapsed) form used for grouping; {@code keywordDisplay} keeps the
 * original for display. {@code createdAtEpoch} enables a clean numeric range query.
 */
@Document(indexName = "shopping-searches", createIndex = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchLogDocument {
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String keyword;

    @Field(type = FieldType.Keyword)
    private String keywordDisplay;

    @Field(type = FieldType.Keyword)
    private String userEmail;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Integer)
    private int resultCount;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    @Field(type = FieldType.Long)
    private long createdAtEpoch;

    public SearchLogDocument(
            String keyword,
            String keywordDisplay,
            String userEmail,
            String category,
            int resultCount,
            Instant createdAt
    ) {
        this.keyword = keyword;
        this.keywordDisplay = keywordDisplay;
        this.userEmail = userEmail;
        this.category = category;
        this.resultCount = resultCount;
        this.createdAt = createdAt;
        this.createdAtEpoch = createdAt.toEpochMilli();
    }
}
