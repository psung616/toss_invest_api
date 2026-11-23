package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.PriceDto;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 시세 조회 도구.
 *
 * TODO(P3-3): get_candles, get_exchange_rate, get_market_status, record 반환 + generateOutputSchema
 */
@Component
public class MarketTools {

    private final TossApi tossApi;

    public MarketTools(TossApi tossApi) {
        this.tossApi = tossApi;
    }

    @McpTool(
            name = "get_quote",
            description = "종목의 현재가를 조회할 때 사용합니다. 입력은 종목 코드(symbol)이며, 종목명만 알면 먼저 종목 코드를 확인하세요.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getQuote(
            @McpToolParam(description = "종목 코드. 국내 6자리(예: 005930), 미국 티커(예: AAPL). 쉼표로 여러 개 가능", required = true)
                    String symbol) {
        return ToolErrors.handle(() ->
                tossApi.getPrices(symbol).stream().map(MarketTools::format).collect(Collectors.joining("\n")));
    }

    private static String format(PriceDto p) {
        return "%s 현재가: %s %s".formatted(p.symbol(), p.lastPrice().toPlainString(), p.currency());
    }
}
