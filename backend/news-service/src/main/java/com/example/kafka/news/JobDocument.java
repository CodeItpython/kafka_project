package com.example.kafka.news;

import com.example.kafka.news.YouthDtos.YouthJob;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 경기 공공일자리(잡아바) 채용공고를 Elasticsearch에 배치 색인한 문서.
 * {@link JobIndexService}가 주기적으로 openapi.gg.go.kr에서 전량을 받아 upsert하고,
 * {@code YouthService.jobs()}가 여기서 페이징 조회한다(상시 프록시 대신 색인 서빙 → 재기동/업스트림 장애에 강함).
 *
 * <p>표시값은 이미 정제/포맷된 문자열이라 keyword(index=false)로 저장한다(분석/검색 불필요, 저장·정렬만).
 * {@code seq}는 원본 API 순서 보존용 정렬 키, {@code indexedAt}은 배치 세대 구분용(오래된 세대 정리에 사용).</p>
 */
@Document(indexName = "gg-jobs", createIndex = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobDocument {
    @Id
    private String id;

    @Field(type = FieldType.Keyword, index = false)
    private String title;

    @Field(type = FieldType.Keyword, index = false)
    private String org;

    @Field(type = FieldType.Keyword, index = false)
    private String startDate;

    @Field(type = FieldType.Keyword, index = false)
    private String endDate;

    @Field(type = FieldType.Keyword, index = false)
    private String period;

    @Field(type = FieldType.Keyword, index = false)
    private String url;

    // 원본 API 순서 보존용 정렬 키(색인 서빙 시 라이브와 같은 순서로 노출).
    @Field(type = FieldType.Integer)
    private Integer seq;

    // 배치 세대. 이번 실행분보다 오래된 문서(=업스트림에서 사라진 공고)를 정리하는 데 쓴다.
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant indexedAt;

    public JobDocument(String id, String title, String org, String startDate, String endDate,
                       String period, String url, Integer seq, Instant indexedAt) {
        this.id = id;
        this.title = title;
        this.org = org;
        this.startDate = startDate;
        this.endDate = endDate;
        this.period = period;
        this.url = url;
        this.seq = seq;
        this.indexedAt = indexedAt;
    }

    public YouthJob toItem() {
        return new YouthJob(id, title, org, startDate, endDate, period, url);
    }

    /**
     * 안정적 문서 id: (url|title|org|startDate) 해시. 상세 URL이 공고마다 유일하지 않을 수 있어(예: 기관 채용 메인
     * URL 공유) 제목·기관·시작일을 함께 넣는다. 재색인 시 같은 공고는 같은 id로 덮어써 중복을 막는다.
     */
    public static JobDocument from(YouthJob job, int seq, Instant indexedAt) {
        String basis = nullSafe(job.url()) + "|" + nullSafe(job.title())
                + "|" + nullSafe(job.org()) + "|" + nullSafe(job.startDate());
        String id = UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)).toString();
        return new JobDocument(id, job.title(), job.org(), job.startDate(), job.endDate(),
                job.period(), job.url(), seq, indexedAt);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
