package com.tradingsaas.tradingcore.adapter.out.news;

import com.tradingsaas.tradingcore.domain.port.out.NewsContextProvider.NewsHeadline;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleNewsRssAdapterTest {

    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    private MockWebServer server;
    private GoogleNewsRssAdapter adapter;

    @BeforeEach void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        adapter = new GoogleNewsRssAdapter(
                WebClient.builder().baseUrl(server.url("/").toString()).build(), 5);
    }

    @AfterEach void tearDown() throws IOException { server.shutdown(); }

    private String rssWithFreshItem(String pubDate) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel>"
                + "<item><title>Apple breaks records on iPhone demand</title>"
                + "<pubDate>" + pubDate + "</pubDate>"
                + "<source url=\"https://reuters.com\">Reuters</source></item>"
                + "</channel></rss>";
    }

    @Test void returnsHeadlinesPublishedWithin48Hours() {
        String fresh = ZonedDateTime.now().minusHours(1).format(RFC_822);
        server.enqueue(new MockResponse().setBody(rssWithFreshItem(fresh))
                .addHeader("Content-Type", "application/xml; charset=utf-8"));

        List<NewsHeadline> headlines = adapter.fetchHeadlines("AAPL");

        assertThat(headlines).hasSize(1);
        assertThat(headlines.get(0).title()).isEqualTo("Apple breaks records on iPhone demand");
        assertThat(headlines.get(0).source()).isEqualTo("Reuters");
    }

    @Test void filtersOutStaleHeadlines() {
        String stale = ZonedDateTime.now().minusHours(49).format(RFC_822);
        server.enqueue(new MockResponse().setBody(rssWithFreshItem(stale))
                .addHeader("Content-Type", "application/xml; charset=utf-8"));

        List<NewsHeadline> headlines = adapter.fetchHeadlines("AAPL");

        assertThat(headlines).isEmpty();
    }

    @Test void returnsEmptyListOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<NewsHeadline> headlines = adapter.fetchHeadlines("AAPL");

        assertThat(headlines).isEmpty();
    }

    @Test void returnsEmptyListOnMalformedXml() {
        server.enqueue(new MockResponse().setBody("not xml at all")
                .addHeader("Content-Type", "application/xml"));

        List<NewsHeadline> headlines = adapter.fetchHeadlines("AAPL");

        assertThat(headlines).isEmpty();
    }

    @Test void capsResultsAt10Headlines() {
        StringBuilder items = new StringBuilder();
        String fresh = ZonedDateTime.now().minusHours(1).format(RFC_822);
        for (int i = 0; i < 15; i++) {
            items.append("<item><title>Headline ").append(i).append("</title>")
                 .append("<pubDate>").append(fresh).append("</pubDate>")
                 .append("<source url=\"x\">Src</source></item>");
        }
        String rss = "<?xml version=\"1.0\"?><rss><channel>" + items + "</channel></rss>";
        server.enqueue(new MockResponse().setBody(rss)
                .addHeader("Content-Type", "application/xml"));

        List<NewsHeadline> headlines = adapter.fetchHeadlines("AAPL");

        assertThat(headlines).hasSize(10);
    }
}
