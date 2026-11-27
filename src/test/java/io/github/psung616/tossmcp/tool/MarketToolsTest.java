package io.github.psung616.tossmcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.PriceDto;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.stock.StockEntry;
import io.github.psung616.tossmcp.stock.StockMasterCache;
import io.github.psung616.tossmcp.stock.StockSearch;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P9-3: MCP 도구 문구 고정 (서비스 추출 전후로 같아야 한다). 모든 값은 지어낸 가짜 값. */
class MarketToolsTest {

    private final TossApi tossApi = mock(TossApi.class);
    private final StockMasterCache stockMaster = mock(StockMasterCache.class);
    private MarketTools tools;

    @BeforeEach
    void setUp() {
        when(stockMaster.search())
                .thenReturn(new StockSearch(List.of(
                        new StockEntry("005930", "삼성전자", "KOSPI", "STOCK", true),
                        new StockEntry("005935", "삼성전자우", "KOSPI", "STOCK", false),
                        new StockEntry("NVDA", "엔비디아", "NASDAQ", "STOCK", true))));
        tools = ToolFixtures.marketTools(tossApi, stockMaster);
    }

    @Test
    void searchListsSymbolNameMarketAndPreferredShare() {
        assertThat(tools.searchStock("삼성전자", null, null)).isEqualTo("005930 삼성전자 (KOSPI)\n005935 삼성전자우 (KOSPI, 우선주)");
    }

    @Test
    void searchHonorsMarketAndLimit() {
        assertThat(tools.searchStock("삼성전자", "KR", 1)).isEqualTo("005930 삼성전자 (KOSPI)");
        assertThat(tools.searchStock("엔비디아", "US", null)).isEqualTo("NVDA 엔비디아 (NASDAQ)");
    }

    @Test
    void searchWithNoMatch() {
        assertThat(tools.searchStock("없는종목", null, null)).isEqualTo("'없는종목' 에 해당하는 종목이 없습니다.");
    }

    @Test
    void quoteLinePerSymbol() {
        when(tossApi.getPrices("005930,NVDA"))
                .thenReturn(List.of(
                        new PriceDto("005930", "2026-11-27T10:00:00+09:00", new BigDecimal("71000"), "KRW"),
                        new PriceDto("NVDA", "2026-11-27T10:00:00+09:00", new BigDecimal("123.45"), "USD")));

        assertThat(tools.getQuote("005930,NVDA")).isEqualTo("005930 현재가: 71000 KRW\nNVDA 현재가: 123.45 USD");
    }

    @Test
    void tossErrorBecomesUserMessage() {
        when(tossApi.getPrices("005930"))
                .thenThrow(new TossApiException(403, "ip-not-allowed", "IP not allowed", "req-9"));

        assertThat(tools.getQuote("005930")).startsWith("[토스증권 API 오류] ").contains("req-9");
    }
}
