package com.example.kafka.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kafka.news.NewsDtos.NewsItem;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NaverNewsClientTest {

    private NaverNewsClient client() {
        NaverNewsClient client = new NaverNewsClient();
        ReflectionTestUtils.setField(client, "itemLimit", 30);
        return client;
    }

    @Test
    void parseExtractsArticleLinksAndSkipsNonArticleLinks() {
        String html = """
            <html><body>
              <a href="/menu">메뉴</a>
              <a href="https://n.news.naver.com/mnews/article/025/0003412345">
                <img data-src="https://img.example.com/a.jpg" alt="썸네일">
                <strong class="sa_text_strong">코스피 2600선 회복… 외국인 순매수 전환</strong>
              </a>
              <a href="https://n.news.naver.com/mnews/article/056/0001234567">
                <span class="sa_text_title">환율 급등에 수출기업 실적 비상</span>
              </a>
              <a href="https://www.naver.com">네이버</a>
            </body></html>
            """;
        Document doc = Jsoup.parse(html, "https://news.naver.com/");
        List<NewsItem> items = client().parse(doc);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).id()).isEqualTo("025_0003412345");
        assertThat(items.get(0).title()).contains("코스피");
        assertThat(items.get(0).url()).contains("/article/025/0003412345");
        assertThat(items.get(0).thumbnail()).isEqualTo("https://img.example.com/a.jpg");
        assertThat(items.get(1).id()).isEqualTo("056_0001234567");
    }

    @Test
    void parseMergesImageOnlyAndTitleOnlyLinksForSameArticle() {
        String html = """
            <html><body>
              <a href="https://n.news.naver.com/mnews/article/009/0005556666">
                <img src="https://img.example.com/thumb.jpg">
              </a>
              <a href="https://n.news.naver.com/mnews/article/009/0005556666">
                <strong>삼성전자 신형 반도체 양산 시작</strong>
              </a>
            </body></html>
            """;
        Document doc = Jsoup.parse(html, "https://news.naver.com/");
        List<NewsItem> items = client().parse(doc);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).contains("삼성전자");
        assertThat(items.get(0).thumbnail()).isEqualTo("https://img.example.com/thumb.jpg");
    }
}
