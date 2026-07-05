package com.example.kafka.news;

import com.example.kafka.news.NewsDtos.NewsItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 네이버 뉴스 섹션/랭킹 페이지를 Jsoup으로 파싱한다.
 * 레이아웃 클래스에 의존하지 않고 "기사 URL 패턴을 가진 링크"를 모두 수집한 뒤
 * 같은 기사(oid/aid)끼리 제목·썸네일을 병합하는 방식이라 마크업 변경에 비교적 강하다.
 */
@Slf4j
@Component
public class NaverNewsClient {

    // /article/025/0003412345 또는 /mnews/article/025/0003412345 형태에서 oid/aid 추출
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("/article/(\\d{2,4})/(\\d{6,})");
    // 제목 후보로 우선 살펴볼 자식 요소들
    private static final String TITLE_SELECTOR =
            ".sa_text_strong, .sa_text_title, .cluster_text_headline, .list_title, .tit, strong, .rankingnews_title";

    @Value("${app.news.user-agent}")
    private String userAgent;

    @Value("${app.news.request-timeout-ms:8000}")
    private int timeoutMs;

    @Value("${app.news.item-limit:30}")
    private int itemLimit;

    public List<NewsItem> fetch(NewsCategory category) {
        try {
            Document doc = Jsoup.connect(category.url())
                    .userAgent(userAgent)
                    .referrer("https://www.naver.com")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .timeout(timeoutMs)
                    .get();
            List<NewsItem> items = parse(doc);
            log.info("Fetched {} news items for category={}", items.size(), category.code());
            return items;
        } catch (IOException exception) {
            log.warn("Failed to crawl Naver news category={} url={}: {}", category.code(), category.url(), exception.toString());
            return List.of();
        }
    }

    List<NewsItem> parse(Document doc) {
        // id -> 누적 필드(제목/썸네일이 여러 링크에 흩어져 있을 수 있어 병합)
        Map<String, String[]> accumulator = new LinkedHashMap<>(); // [title, url, thumbnail]
        Elements anchors = doc.select("a[href]");
        for (Element anchor : anchors) {
            String href = anchor.absUrl("href");
            if (href.isEmpty()) {
                href = anchor.attr("href");
            }
            Matcher matcher = ARTICLE_PATTERN.matcher(href);
            if (!matcher.find()) {
                continue;
            }
            String id = matcher.group(1) + "_" + matcher.group(2);
            String url = normalizeArticleUrl(matcher.group(1), matcher.group(2), href);
            String title = extractTitle(anchor);
            String thumbnail = extractThumbnail(anchor);

            String[] current = accumulator.computeIfAbsent(id, key -> new String[]{"", url, null});
            if ((current[0] == null || current[0].isBlank()) && title != null && !title.isBlank()) {
                current[0] = title;
            }
            if (current[2] == null && thumbnail != null) {
                current[2] = thumbnail;
            }
        }

        List<NewsItem> items = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : accumulator.entrySet()) {
            String[] value = entry.getValue();
            String title = value[0];
            if (title == null || title.isBlank() || title.length() < 6) {
                continue; // 제목 없는(이미지 전용) 링크는 스킵
            }
            items.add(new NewsItem(entry.getKey(), clean(title), value[1], null, value[2]));
            if (items.size() >= itemLimit) {
                break;
            }
        }
        return items;
    }

    private String extractTitle(Element anchor) {
        Element titleEl = anchor.selectFirst(TITLE_SELECTOR);
        if (titleEl != null && !titleEl.text().isBlank()) {
            return titleEl.text();
        }
        String own = anchor.text();
        if (own != null && !own.isBlank()) {
            return own;
        }
        // 텍스트가 없으면 이미지 alt를 제목 후보로
        Element img = anchor.selectFirst("img[alt]");
        return img != null ? img.attr("alt") : null;
    }

    private String extractThumbnail(Element anchor) {
        Element img = anchor.selectFirst("img");
        if (img == null) {
            return null;
        }
        for (String attr : new String[]{"data-src", "data-lazysrc", "data-original", "src"}) {
            String value = img.hasAttr(attr) ? img.attr(attr) : "";
            if (value != null && value.startsWith("http")) {
                return value;
            }
            if (value != null && value.startsWith("//")) {
                return "https:" + value;
            }
        }
        return null;
    }

    private String normalizeArticleUrl(String oid, String aid, String fallback) {
        // 모바일 정식 기사 URL로 정규화 (클릭 시 실제 기사로 이동)
        if (fallback != null && fallback.startsWith("http")) {
            return fallback;
        }
        return "https://n.news.naver.com/mnews/article/" + oid + "/" + aid;
    }

    private String clean(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
