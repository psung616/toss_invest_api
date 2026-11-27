package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.service.QuoteService;
import io.github.psung616.tossmcp.service.QuoteService.Quote;
import io.github.psung616.tossmcp.service.StockSearchService;
import io.github.psung616.tossmcp.service.StockSearchService.StockMatch;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 시세 조회 도구. 조회는 {@code service} 가 하고, 여기서는 Claude 에게 보낼 문장으로만 바꾼다(P9-3 — web 과 같은 서비스 공유).
 *
 * TODO(P3-3): get_candles, get_exchange_rate, get_market_status, record 반환 + generateOutputSchema
 */
@Component
public class MarketTools {

    private final QuoteService quotes;
    private final StockSearchService stocks;

    public MarketTools(QuoteService quotes, StockSearchService stocks) {
        this.quotes = quotes;
        this.stocks = stocks;
    }

    @McpTool(
            name = "search_stock",
            description = "종목명으로 종목 코드(symbol)를 찾을 때 사용합니다. 예: '삼성전자' -> 005930, '엔비디아' -> NVDA. "
                    + "다른 도구에는 항상 여기서 찾은 symbol 을 넘기세요. 첫 호출은 종목 목록을 받느라 몇 초 걸릴 수 있습니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String searchStock(
            @McpToolParam(description = "종목명 또는 코드 일부 (예: 삼성, 하이닉스, NVDA)", required = true) String query,
            @McpToolParam(description = "시장 필터: KR(국내) 또는 US(미국). 생략하면 전체", required = false) String market,
            @McpToolParam(description = "최대 결과 수 (기본 10, 최대 50)", required = false) Integer limit) {
        return ToolErrors.handle(() -> {
            List<StockMatch> found = stocks.search(query, market, limit);
            if (found.isEmpty()) {
                return "'" + query + "' 에 해당하는 종목이 없습니다.";
            }
            return found.stream()
                    .map(e ->
                            "%s %s (%s%s)".formatted(e.symbol(), e.name(), e.market(), e.commonShare() ? "" : ", 우선주"))
                    .collect(Collectors.joining("\n"));
        });
    }

    @McpTool(
            name = "get_quote",
            description = "종목의 현재가를 조회할 때 사용합니다. 입력은 종목 코드(symbol)이며, 종목명만 알면 먼저 종목 코드를 확인하세요.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getQuote(
            @McpToolParam(description = "종목 코드. 국내 6자리(예: 005930), 미국 티커(예: AAPL). 쉼표로 여러 개 가능", required = true)
                    String symbol) {
        return ToolErrors.handle(
                () -> quotes.quotes(symbol).stream().map(MarketTools::format).collect(Collectors.joining("\n")));
    }

    private static String format(Quote q) {
        return "%s 현재가: %s %s".formatted(q.symbol(), q.lastPrice().toPlainString(), q.currency());
    }
}
