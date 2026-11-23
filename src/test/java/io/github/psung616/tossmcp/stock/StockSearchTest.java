package io.github.psung616.tossmcp.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 손으로 만든 가짜 종목 마스터. */
class StockSearchTest {

    private final StockSearch search = new StockSearch(List.of(
            new StockEntry("005930", "삼성전자", "KOSPI", "STOCK", true),
            new StockEntry("005935", "삼성전자우", "KOSPI", "STOCK", false),
            new StockEntry("006400", "삼성SDI", "KOSPI", "STOCK", true),
            new StockEntry("207940", "삼성바이오로직스", "KOSPI", "STOCK", true),
            new StockEntry("000660", "SK하이닉스", "KOSPI", "STOCK", true),
            new StockEntry("NVDA", "엔비디아", "NASDAQ", "STOCK", true),
            new StockEntry("AAPL", "애플", "NASDAQ", "STOCK", true)));

    private List<String> symbols(String q) {
        return search.search(q, null, 10).stream().map(StockEntry::symbol).toList();
    }

    @Test
    void exactNameRanksFirst() {
        assertThat(symbols("삼성전자")).first().isEqualTo("005930");
        assertThat(symbols("삼성전자")).containsExactly("005930", "005935");
    }

    @Test
    void prefixReturnsCommonSharesFirst() {
        assertThat(symbols("삼성")).first().isEqualTo("005930");
        assertThat(symbols("삼성")).hasSize(4).doesNotContain("000660");
    }

    @Test
    void symbolExactMatch() {
        assertThat(symbols("005930")).first().isEqualTo("005930");
        assertThat(symbols("nvda")).containsExactly("NVDA");
    }

    @Test
    void ignoresSpacesAndCase() {
        assertThat(symbols("삼성 전자")).first().isEqualTo("005930");
        assertThat(symbols("sk하이닉스")).containsExactly("000660");
    }

    @Test
    void marketFilter() {
        assertThat(search.search("엔비디아", "KR", 10)).isEmpty();
        assertThat(search.search("엔비디아", "US", 10))
                .extracting(StockEntry::symbol)
                .containsExactly("NVDA");
    }

    @Test
    void exactLookup() {
        assertThat(search.exact("삼성전자")).map(StockEntry::symbol).contains("005930");
        assertThat(search.exact("AAPL")).map(StockEntry::name).contains("애플");
        assertThat(search.exact("삼성")).isEmpty();
    }

    @Test
    void blankOrUnknownQuery() {
        assertThat(symbols(" ")).isEmpty();
        assertThat(symbols("존재하지않는종목")).isEmpty();
    }
}
