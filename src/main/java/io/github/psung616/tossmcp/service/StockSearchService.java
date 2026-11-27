package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.stock.StockMasterCache;
import java.util.List;
import org.springframework.stereotype.Service;

/** 종목 검색 (P9-3). MCP {@code search_stock}·web {@code /api/stocks/search} 가 공유한다. */
@Service
public class StockSearchService {

    public static final int DEFAULT_LIMIT = 10;

    /** 검색 결과 한 줄. {@code commonShare=false} 면 우선주. */
    public record StockMatch(String symbol, String name, String market, boolean commonShare) {}

    private final StockMasterCache stockMaster;

    public StockSearchService(StockMasterCache stockMaster) {
        this.stockMaster = stockMaster;
    }

    /**
     * @param market KR·US 또는 null(전체)
     * @param limit null 이면 {@value #DEFAULT_LIMIT}. 1~50 으로 맞춘다(StockSearch)
     */
    public List<StockMatch> search(String query, String market, Integer limit) {
        return stockMaster.search().search(query, market, limit == null ? DEFAULT_LIMIT : limit).stream()
                .map(e -> new StockMatch(e.symbol(), e.name(), e.market(), e.commonShare()))
                .toList();
    }
}
