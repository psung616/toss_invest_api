package io.github.psung616.tossmcp.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.service.AccountQueryService;
import io.github.psung616.tossmcp.service.AccountQueryService.BuyingPower;
import io.github.psung616.tossmcp.service.AccountQueryService.OrderItem;
import io.github.psung616.tossmcp.service.AppStatusService;
import io.github.psung616.tossmcp.service.AppStatusService.AppStatus;
import io.github.psung616.tossmcp.service.QuoteService;
import io.github.psung616.tossmcp.service.QuoteService.Quote;
import io.github.psung616.tossmcp.service.StockSearchService;
import io.github.psung616.tossmcp.service.StockSearchService.StockMatch;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;

/**
 * P9-4 조회 REST (ARCHITECTURE §12.3, WEB_SECURITY WS-REQ-8·WS-RES-3·5·WS-DATA-1). 세션·Host 검사는 {@code LocalAccessFilterTest} 가 본다. 서비스는 가짜,
 * 값은 모두 지어낸 것.
 */
class QueryControllerTest {

    private final StockSearchService stocks = mock(StockSearchService.class);
    private final QuoteService quotes = mock(QuoteService.class);
    private final AccountQueryService account = mock(AccountQueryService.class);
    private final AppStatusService appStatus = mock(AppStatusService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(appStatus.status()).thenReturn(new AppStatus("0.1.0", true, false, false, true));
        mvc = MockMvcBuilders.standaloneSetup(new QueryController(stocks, quotes, account))
                .setControllerAdvice(new ApiErrorAdvice(appStatus))
                .build();
    }

    // ---- 정상 응답

    @Test
    void searchReturnsMatches() throws Exception {
        when(stocks.search("삼성전자", "KR", 2))
                .thenReturn(List.of(
                        new StockMatch("005930", "삼성전자", "KOSPI", true),
                        new StockMatch("005935", "삼성전자우", "KOSPI", false)));

        mvc.perform(get("/api/stocks/search")
                        .param("query", " 삼성전자 ")
                        .param("market", "kr")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].symbol").value("005930"))
                .andExpect(jsonPath("$.results[1].commonShare").value(false));
    }

    @Test
    void quoteSendsAmountsAsDecimalStrings() throws Exception {
        when(quotes.quotes("005930,AAPL"))
                .thenReturn(List.of(
                        new Quote("005930", new BigDecimal("71000"), "KRW", "2026-11-27T10:00:00+09:00"),
                        new Quote("AAPL", new BigDecimal("1E+2"), "USD", "2026-11-27T10:00:00+09:00")));

        mvc.perform(get("/api/quote").param("symbol", "005930, aapl"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.quotes[0].lastPrice").value("71000"))
                .andExpect(jsonPath("$.quotes[1].lastPrice").value("100"))
                .andExpect(jsonPath("$.quotes[1].currency").value("USD"));
    }

    @Test
    void buyingPowerDefaultsAndAmountAsString() throws Exception {
        when(account.buyingPower(null)).thenReturn(new BuyingPower("KRW", new BigDecimal("1234567.00")));

        mvc.perform(get("/api/buying-power"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.cash").value("1234567.00"));
    }

    @Test
    void ordersDefaultToOpenWithNullableAmounts() throws Exception {
        when(account.orders("OPEN"))
                .thenReturn(List.of(new OrderItem(
                        "ord-1", "NVDA", "SELL", "MARKET", "OPEN", null, new BigDecimal("2"), "USD", null)));

        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.orders[0].quantity").value("2"))
                .andExpect(jsonPath("$.orders[0].price").doesNotExist());
        verify(account).orders("OPEN");
    }

    // ---- 입력 검증 (WS-REQ-8)

    @Test
    void invalidInputsAre400AndNeverReachServices() throws Exception {
        String[][] bad = {
            {"/api/stocks/search", "query", ""},
            {"/api/stocks/search", "query", "가".repeat(51)},
            {"/api/quote", "symbol", ""},
            {"/api/quote", "symbol", "005930;DROP"},
            {"/api/quote", "symbol", "<script>"},
            {"/api/quote", "symbol", "A".repeat(21)},
            {"/api/quote", "symbol", "1,2,3,4,5,6,7,8,9,10,11"},
            {"/api/quote", "symbol", "005930,"},
            {"/api/buying-power", "currency", "JPY"},
            {"/api/orders", "status", "ALL"},
        };
        for (String[] b : bad) {
            String body = mvc.perform(get(b[0]).param(b[1], b[2]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("invalid_input"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(body)
                    .as("입력값을 되돌려 주지 않는다: %s", b[2])
                    .doesNotContain("<script>")
                    .doesNotContain("DROP");
        }
        mvc.perform(get("/api/quote")).andExpect(status().isBadRequest());
        verifyNoInteractions(quotes, account);
    }

    @Test
    void searchLimitAndMarketValidated() throws Exception {
        for (String limit : List.of("0", "11", "abc", "-1", "999999999999")) {
            mvc.perform(get("/api/stocks/search").param("query", "삼성").param("limit", limit))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/stocks/search").param("query", "삼성").param("market", "JP"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(stocks);
    }

    // ---- 에러 정규화 (WS-RES-5)

    @Test
    void tossErrorsAreNormalized() throws Exception {
        Object[][] cases = {
            {403, "ip-not-allowed", HttpStatus.BAD_GATEWAY},
            {401, "invalid_client", HttpStatus.BAD_GATEWAY},
            {429, "rate-limit-exceeded", HttpStatus.TOO_MANY_REQUESTS},
            {404, "stock-not-found", HttpStatus.NOT_FOUND},
            {400, "bad-request", HttpStatus.BAD_REQUEST},
            {500, "maintenance", HttpStatus.SERVICE_UNAVAILABLE},
            {0, null, HttpStatus.BAD_GATEWAY},
        };
        for (Object[] c : cases) {
            doThrow(new TossApiException((int) c[0], (String) c[1], "raw toss message", "req-1"))
                    .when(quotes)
                    .quotes("005930");
            mvc.perform(get("/api/quote").param("symbol", "005930"))
                    .andExpect(status().is(((HttpStatus) c[2]).value()))
                    .andExpect(jsonPath("$.error.code").value(c[1] == null ? "toss_error" : c[1]))
                    .andExpect(jsonPath("$.error.requestId").value("req-1"))
                    .andExpect(jsonPath("$.error.message").isString());
        }
    }

    @Test
    void tossAuthFailureIsNever401() throws Exception {
        when(account.buyingPower(any())).thenThrow(new TossApiException(401, "invalid_client", "x", null));

        mvc.perform(get("/api/buying-power")).andExpect(status().isBadGateway());
    }

    @Test
    void networkFailureIs503() throws Exception {
        when(account.orders(anyString())).thenThrow(new ResourceAccessException("Connect timed out: some.internal"));

        String body = mvc.perform(get("/api/orders"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("toss_unreachable"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("some.internal");
    }

    @Test
    void missingKeysIs409WithGuide() throws Exception {
        when(appStatus.status()).thenReturn(new AppStatus("0.1.0", false, false, false, true));
        when(account.buyingPower(any())).thenThrow(new IllegalStateException("API 키가 설정되지 않았습니다."));

        mvc.perform(get("/api/buying-power"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("keys_missing"));
    }

    @Test
    void unexpectedErrorsRevealNothing() throws Exception {
        when(quotes.quotes(anyString())).thenThrow(new IllegalStateException("C:/secret/path boom"));

        String body = mvc.perform(get("/api/quote").param("symbol", "005930"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("internal_error"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("boom").doesNotContain("secret").doesNotContain("Exception");
    }
}
