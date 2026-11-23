package io.github.psung616.tossmcp.stock;

/** 종목 마스터 한 줄. market 은 KOSPI / KOSDAQ / NASDAQ / NYSE / AMEX. */
public record StockEntry(String symbol, String name, String market, String securityType, boolean commonShare) {

    public boolean isKorean() {
        return "KOSPI".equals(market) || "KOSDAQ".equals(market);
    }
}
