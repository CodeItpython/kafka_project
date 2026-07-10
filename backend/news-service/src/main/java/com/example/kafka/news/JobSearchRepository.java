package com.example.kafka.news;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** 경기 공공일자리 색인 저장소. 배치 upsert + 페이징 조회에 사용(쿼리 메서드 불필요, findAll(Pageable)로 충분). */
public interface JobSearchRepository extends ElasticsearchRepository<JobDocument, String> {
}
