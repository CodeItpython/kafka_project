package com.example.kafka.news;

import com.example.kafka.news.NewsDtos.NewsItem;
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
 * 검색·연관검색어용으로 색인되는 뉴스 기사. 피드/검색에서 가져온 기사를 기사 URL(id)로 upsert한다.
 * {@code contentNori}(제목+요약을 한국어 형태소 nori로 분석)에 significant_text 집계를 돌려 연관어를 뽑는다.
 */
@Document(indexName = "news-articles", createIndex = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsDocument {
    @Id
    private String id;

    @Field(type = FieldType.Keyword, index = false)
    private String title;

    // 제목을 search_as_you_type으로 색인 — 자동완성(prefix) 전용.
    @Field(type = FieldType.Search_As_You_Type, maxShingleSize = 3)
    private String titleSuggest;

    // 제목+요약을 nori로 분석한 필드 — 연관검색어(significant_text) 대상.
    @Field(type = FieldType.Text, analyzer = "nori")
    private String contentNori;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant indexedAt;

    public NewsDocument(String id, String title, String contentNori, String category, Instant indexedAt) {
        this.id = id;
        this.title = title;
        this.titleSuggest = title;
        this.contentNori = contentNori;
        this.category = category;
        this.indexedAt = indexedAt;
    }

    public static NewsDocument from(NewsItem item, String category, Instant indexedAt) {
        String title = item.title() == null ? "" : item.title();
        String description = item.description() == null ? "" : item.description();
        String content = (title + " " + description).trim();
        return new NewsDocument(item.url(), title, content, category, indexedAt);
    }
}
