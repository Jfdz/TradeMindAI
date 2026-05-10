package com.tradingsaas.tradingcore.adapter.out.news;

import com.tradingsaas.tradingcore.domain.port.out.NewsContextProvider.NewsHeadline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeNewsContextAdapterTest {

    @Mock GoogleNewsRssAdapter rssAdapter;
    @InjectMocks CompositeNewsContextAdapter composite;

    @Test void delegatesToRssAdapterInRssOnlyMode() {
        List<NewsHeadline> expected = List.of(
                new NewsHeadline("AAPL surges", "Reuters", Instant.now()));
        when(rssAdapter.fetchHeadlines("AAPL")).thenReturn(expected);

        List<NewsHeadline> result = composite.fetchHeadlines("AAPL");

        assertThat(result).isEqualTo(expected);
    }

    @Test void returnsEmptyListWhenRssReturnsNothing() {
        when(rssAdapter.fetchHeadlines("TSLA")).thenReturn(List.of());

        List<NewsHeadline> result = composite.fetchHeadlines("TSLA");

        assertThat(result).isEmpty();
    }
}
