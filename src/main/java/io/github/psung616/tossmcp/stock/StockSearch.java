package io.github.psung616.tossmcp.stock;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 종목 검색 (네트워크 없음, 순수 로직). 순위: 심볼 정확 일치 > 이름 정확 일치 > 이름 접두 > 이름·심볼 부분 일치.
 * 같은 순위 안에서는 보통주 > 국내 > 짧은 이름 순. 비교 시 공백·대소문자·유니코드 정규화(NFC) 차이를 무시한다.
 */
public final class StockSearch {

    private final List<StockEntry> entries;

    public StockSearch(List<StockEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<StockEntry> search(String query, String market, int limit) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(e -> market == null || market.isBlank() || matchesMarket(e, market))
                .map(e -> new Scored(e, score(e, q)))
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingInt((Scored s) -> -s.score)
                        .thenComparing(s -> !s.entry.commonShare())
                        .thenComparing(s -> !s.entry.isKorean())
                        .thenComparingInt(s -> s.entry.name().length()))
                .limit(Math.max(1, Math.min(limit, 50)))
                .map(Scored::entry)
                .toList();
    }

    /** 심볼 또는 이름이 정확히 하나로 확정되면 그 종목. */
    public Optional<StockEntry> exact(String query) {
        String q = normalize(query);
        List<StockEntry> exact = entries.stream()
                .filter(e ->
                        normalize(e.symbol()).equals(q) || normalize(e.name()).equals(q))
                .toList();
        return exact.size() == 1 ? Optional.of(exact.get(0)) : Optional.empty();
    }

    public int size() {
        return entries.size();
    }

    private static int score(StockEntry e, String q) {
        String symbol = normalize(e.symbol());
        String name = normalize(e.name());
        if (symbol.equals(q)) {
            return 100;
        }
        if (name.equals(q)) {
            return 90;
        }
        if (name.startsWith(q)) {
            return 70;
        }
        if (name.contains(q)) {
            return 50;
        }
        if (symbol.startsWith(q)) {
            return 40;
        }
        return 0;
    }

    private static boolean matchesMarket(StockEntry e, String market) {
        String m = market.toUpperCase(Locale.ROOT);
        if ("KR".equals(m)) {
            return e.isKorean();
        }
        if ("US".equals(m)) {
            return !e.isKorean();
        }
        return m.equals(e.market());
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFC)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private record Scored(StockEntry entry, int score) {}
}
