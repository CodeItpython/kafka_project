package com.example.kafka.news;

import com.example.kafka.news.NewsDtos.LinkPreview;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 채팅 링크 공유용 Open Graph 미리보기. 외부 URL을 서버에서 가져오므로 SSRF에 유의해
 * http(s)만 허용하고, 사설/루프백/링크로컬/메타데이터 IP로 향하는 요청은 차단한다.
 */
@Slf4j
@Service
public class LinkPreviewService {

    private final String userAgent;
    private final int timeoutMs;
    private final long ttlSeconds;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public LinkPreviewService(
            @Value("${app.news.user-agent}") String userAgent,
            @Value("${app.news.request-timeout-ms:8000}") int timeoutMs,
            @Value("${app.link-preview.cache-ttl-seconds:600}") long ttlSeconds
    ) {
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
        this.ttlSeconds = ttlSeconds;
    }

    public Optional<LinkPreview> preview(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String key = url.trim();
        CacheEntry cached = cache.get(key);
        Instant now = Instant.now();
        if (cached != null && now.isBefore(cached.expiresAt)) {
            return Optional.ofNullable(cached.value);
        }
        LinkPreview preview = fetch(key);
        cache.put(key, new CacheEntry(preview, now.plus(Duration.ofSeconds(ttlSeconds))));
        return Optional.ofNullable(preview);
    }

    private LinkPreview fetch(String url) {
        if (!isFetchable(url)) {
            log.info("Rejected link-preview target (unsafe or invalid): {}", url);
            return null;
        }
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .maxBodySize(2 * 1024 * 1024)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();
            String title = firstNonBlank(
                    meta(doc, "og:title"),
                    meta(doc, "twitter:title"),
                    doc.title());
            String description = firstNonBlank(
                    meta(doc, "og:description"),
                    meta(doc, "twitter:description"),
                    metaName(doc, "description"));
            String image = absolute(doc, firstNonBlank(meta(doc, "og:image"), meta(doc, "twitter:image")));
            String siteName = firstNonBlank(meta(doc, "og:site_name"), host(url));
            LinkPreview preview = new LinkPreview(url, clean(title), clean(description), image, clean(siteName));
            return preview.isEmpty() ? null : preview;
        } catch (Exception exception) {
            log.warn("Failed to build link preview for {}: {}", url, exception.toString());
            return null;
        }
    }

    /** http(s)만 허용하고 사설/로컬 대역 IP를 차단한다. */
    private boolean isFetchable(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String hostName = uri.getHost();
            if (hostName == null || hostName.isBlank()) {
                return false;
            }
            for (InetAddress address : InetAddress.getAllByName(hostName)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    return false;
                }
                // 클라우드 메타데이터(169.254.169.254)는 link-local에 포함되지만 방어적으로 한 번 더 차단
                if ("169.254.169.254".equals(address.getHostAddress())) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException exception) {
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String meta(Document doc, String property) {
        Element el = doc.selectFirst("meta[property=" + property + "]");
        if (el == null) {
            el = doc.selectFirst("meta[name=" + property + "]");
        }
        return el != null ? el.attr("content") : null;
    }

    private String metaName(Document doc, String name) {
        Element el = doc.selectFirst("meta[name=" + name + "]");
        return el != null ? el.attr("content") : null;
    }

    private String absolute(Document doc, String image) {
        if (image == null || image.isBlank()) {
            return null;
        }
        if (image.startsWith("http")) {
            return image;
        }
        if (image.startsWith("//")) {
            return "https:" + image;
        }
        try {
            return URI.create(doc.baseUri()).resolve(image).toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception exception) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String clean(String text) {
        return text == null ? null : text.replaceAll("\\s+", " ").trim();
    }

    private record CacheEntry(LinkPreview value, Instant expiresAt) {
    }
}
