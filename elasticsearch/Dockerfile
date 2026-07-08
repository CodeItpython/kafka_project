# Elasticsearch 8.15.5 + 한국어 형태소 분석기(nori).
# 연관검색어(significant_text)와 한국어 검색 품질을 위해 nori 애널라이저를 제공한다.
# 플러그인 버전은 베이스 이미지 버전에 맞춰 자동 설치된다.
FROM docker.elastic.co/elasticsearch/elasticsearch:8.15.5
RUN bin/elasticsearch-plugin install --batch analysis-nori
