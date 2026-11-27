package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.client.TossApi;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/** 현재가 조회 (P9-3). MCP {@code get_quote}·web {@code /api/quote} 가 공유한다. */
@Service
public class QuoteService {

    public record Quote(String symbol, BigDecimal lastPrice, String currency, String timestamp) {}

    private final TossApi tossApi;

    public QuoteService(TossApi tossApi) {
        this.tossApi = tossApi;
    }

    /** @param symbols 종목 코드, 쉼표로 여러 개 */
    public List<Quote> quotes(String symbols) {
        return tossApi.getPrices(symbols).stream()
                .map(p -> new Quote(p.symbol(), p.lastPrice(), p.currency(), p.timestamp()))
                .toList();
    }
}
