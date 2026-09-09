package com.example.kafka.news;

import com.example.kafka.news.NewsDtos.Article;
import com.example.kafka.news.NewsDtos.ArticleBlock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 기사 URL을 받아 인앱 리더용 본문을 추출한다(외부 페이지로 나가지 않고 앱 안에서 읽기).
 *
 * <p>원본 HTML을 그대로 내려주지 않고 <b>문단/이미지 블록</b>으로 구조화해서 반환한다 —
 * 클라이언트가 {@code dangerouslySetInnerHTML} 없이 렌더할 수 있어 XSS 여지가 없다.</p>
 *
 * <p>본문 선택자는 네이버 뉴스를 우선 시도하고, 실패하면 국내 언론사 CMS에서 흔한 선택자 →
 * {@code <article>} 순으로 폴백한다. 전부 실패하면 빈 blocks 를 돌려주고 프론트가 원문 링크를 안내한다.
 * SSRF 가드는 {@link LinkPreviewService#isFetchable(String)} 을 공유한다.</p>
 */
@Service
@Slf4j
public class ArticleReaderService {
    /** 본문 후보 선택자(앞쪽 우선). 네이버 → 국내 CMS 관용 → 일반. */
    private static final List<String> BODY_SELECTORS = List.of(
            "#dic_area", "#newsct_article", "#articleBodyContents", "#articeBody",
            "#article-view-content-div", "#articleBody", ".article-body", ".article_body",
            ".news-article-body", "#newsEndContents", "article");
    private static final Set<String> SKIP_TAGS =
            Set.of("script", "style", "noscript", "iframe", "form", "button", "svg", "aside", "figcaption");
    private static final Set<String> BLOCK_TAGS =
            Set.of("p", "div", "section", "h1", "h2", "h3", "h4", "li", "blockquote", "figure", "tr", "table");
    /** 기사 끝의 정형 문구(구독 안내·저작권 등)는 리더에서 잘라낸다. */
    private static final List<String> BOILERPLATE = List.of(
            "무단전재", "재배포 금지", "저작권자", "네이버에서 구독", "구독하기", "기사제보", "ⓒ");
    /** 이 길이를 넘는 문단은 본문으로 보고 보일러플레이트 판정에서 제외한다. */
    private static final int BOILERPLATE_MAX_LENGTH = 120;
    private static final int MAX_BLOCKS = 400;
    private static final int MAX_CACHE_ENTRIES = 256;
    /**
     * 리더로 보여줄 최소 본문 분량. 본문을 JS로 렌더하는 사이트(예: ebn.co.kr)는 정적 HTML에 제목만 있어
     * 사진 한 장짜리 "글 없는 리더"가 만들어진다. 그런 경우엔 차라리 원문 링크로 보내는 편이 낫다.
     */
    private static final int MIN_BODY_CHARS = 150;

    private final String userAgent;
    private final int timeoutMs;
    private final long ttlSeconds;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ArticleReaderService(
            @Value("${app.news.user-agent}") String userAgent,
            @Value("${app.news.request-timeout-ms:8000}") int timeoutMs,
            @Value("${app.news.article-cache-ttl-seconds:900}") long ttlSeconds
    ) {
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
        this.ttlSeconds = ttlSeconds;
    }

    public Optional<Article> read(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String key = url.trim();
        Instant now = Instant.now();
        CacheEntry cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return Optional.ofNullable(cached.value());
        }
        Article article = fetch(key);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(key, new CacheEntry(article, now.plus(Duration.ofSeconds(ttlSeconds))));
        return Optional.ofNullable(article);
    }

    private Article fetch(String url) {
        if (!LinkPreviewService.isFetchable(url)) {
            log.info("Rejected article-reader target (unsafe or invalid): {}", url);
            return null;
        }
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .maxBodySize(4 * 1024 * 1024)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();

            Element body = null;
            for (String selector : BODY_SELECTORS) {
                body = doc.selectFirst(selector);
                if (body != null) {
                    break;
                }
            }
            List<ArticleBlock> blocks = body == null ? List.of() : extract(body);
            String title = firstNonBlank(meta(doc, "og:title"), doc.title());
            // 호스트명(n.news.naver.com)으로 폴백하지 않는다 — 보기 흉하고, 프론트가 피드의
            // 언론사명(item.press)으로 대체하는 편이 정확하다.
            String siteName = meta(doc, "og:site_name");
            String publishedAt = firstNonBlank(
                    meta(doc, "article:published_time"),
                    attr(doc, "meta[property=og:article:published_time]", "content"),
                    attr(doc, "time[datetime]", "datetime"),
                    text(doc, ".media_end_head_info_datestamp_time"));
            String leadImage = absolute(doc, meta(doc, "og:image"));
            return new Article(url, clean(title), clean(siteName), clean(publishedAt), leadImage, blocks);
        } catch (Exception exception) {
            log.warn("Failed to read article {}: {}", url, exception.toString());
            return null;
        }
    }

    /** 본문 요소를 문서 순서대로 훑어 문단/이미지 블록으로 만든다(이미지 위치 보존). */
    private static List<ArticleBlock> extract(Element body) {
        List<ArticleBlock> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        walk(body, blocks, buffer);
        flush(buffer, blocks);
        List<ArticleBlock> result = new ArrayList<>(blocks.size());
        String previous = null;
        for (ArticleBlock block : blocks) {
            if ("p".equals(block.type())) {
                if (block.text().equals(previous) || isBoilerplate(block.text())) {
                    continue;
                }
                previous = block.text();
            }
            result.add(block);
            if (result.size() >= MAX_BLOCKS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static void walk(Node node, List<ArticleBlock> blocks, StringBuilder buffer) {
        for (Node child : node.childNodes()) {
            if (blocks.size() >= MAX_BLOCKS) {
                return;
            }
            if (child instanceof TextNode textNode) {
                buffer.append(textNode.getWholeText());
                continue;
            }
            if (!(child instanceof Element element)) {
                continue;
            }
            String tag = element.tagName();
            if (SKIP_TAGS.contains(tag)) {
                continue;
            }
            if ("br".equals(tag)) {
                flush(buffer, blocks);
                continue;
            }
            if ("img".equals(tag)) {
                flush(buffer, blocks);
                String src = element.absUrl("data-src");
                if (src.isBlank()) {
                    src = element.absUrl("src");
                }
                if (!src.isBlank()) {
                    blocks.add(new ArticleBlock("img", null, src));
                }
                continue;
            }
            walk(element, blocks, buffer);
            if (BLOCK_TAGS.contains(tag)) {
                flush(buffer, blocks);
            }
        }
    }

    private static void flush(StringBuilder buffer, List<ArticleBlock> blocks) {
        String paragraph = buffer.toString().replaceAll("\\s+", " ").trim();
        buffer.setLength(0);
        if (paragraph.length() >= 2) {
            blocks.add(new ArticleBlock("p", paragraph, null));
        }
    }

    /**
     * 정형 문구는 <b>짧은 줄일 때만</b> 잘라낸다. 본문이 블록 구분 없이 한 덩어리로 추출되는 사이트가 있어
     * (예: ebn.co.kr) 길이 제한이 없으면 끝의 "무단전재" 한 마디 때문에 기사 전체가 버려진다.
     */
    private static boolean isBoilerplate(String text) {
        if (text.length() > BOILERPLATE_MAX_LENGTH) {
            return false;
        }
        for (String marker : BOILERPLATE) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String meta(Document doc, String property) {
        return attr(doc, "meta[property=" + property + "]", "content");
    }

    private static String attr(Document doc, String selector, String attribute) {
        Element element = doc.selectFirst(selector);
        return element == null ? null : element.attr(attribute);
    }

    private static String text(Document doc, String selector) {
        Element element = doc.selectFirst(selector);
        return element == null ? null : element.text();
    }

    private static String absolute(Document doc, String image) {
        if (image == null || image.isBlank()) {
            return null;
        }
        try {
            return doc.baseUri().isBlank() ? image : java.net.URI.create(doc.baseUri()).resolve(image).toString();
        } catch (IllegalArgumentException exception) {
            return image;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
    }

    /** 리더로 띄울 만한 본문이 있는지(문단 텍스트 총량 기준). 아니면 컨트롤러가 204로 응답한다. */
    static boolean hasReadableBody(Article article) {
        if (article == null || article.blocks() == null) {
            return false;
        }
        int chars = 0;
        for (ArticleBlock block : article.blocks()) {
            if ("p".equals(block.type()) && block.text() != null) {
                chars += block.text().length();
            }
        }
        return chars >= MIN_BODY_CHARS;
    }

    private record CacheEntry(Article value, Instant expiresAt) {
    }
}
