package io.github.psung616.tossmcp.web.api;

import io.github.psung616.tossmcp.service.AccountQueryService;
import io.github.psung616.tossmcp.service.AccountQueryService.BuyingPower;
import io.github.psung616.tossmcp.service.AccountQueryService.OrderItem;
import io.github.psung616.tossmcp.service.QuoteService;
import io.github.psung616.tossmcp.service.QuoteService.Quote;
import io.github.psung616.tossmcp.service.StockSearchService;
import io.github.psung616.tossmcp.service.StockSearchService.StockMatch;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조회 REST (P9-4, ARCHITECTURE §12.3). MCP 도구와 같은 {@code service} 를 쓴다. 세션·Host·Origin 검사는 {@code LocalAccessFilter} 가 먼저 한다.
 *
 * <ul>
 *   <li>입력 검증(WS-REQ-8): symbol 영숫자·{@code .}·{@code -} 1~20자(쉼표로 최대 {@value #MAX_SYMBOLS}개), query 1~50자, limit 1~10,
 *       market·currency·status 는 허용목록 → 아니면 400
 *   <li>금액은 문자열 decimal + currency (WS-RES-3). 키·토큰·accountSeq 는 응답에 없다(WS-DATA-1)
 * </ul>
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class QueryController {

    static final int MAX_SYMBOLS = 10;
    static final int MAX_QUERY_LENGTH = 50;
    static final int MAX_LIMIT = 10;
    private static final Pattern SYMBOL = Pattern.compile("[A-Za-z0-9.\\-]{1,20}");
    private static final Pattern LIMIT = Pattern.compile("\\d{1,2}");
    private static final Set<String> MARKETS = Set.of("KR", "US");
    private static final Set<String> CURRENCIES = Set.of("KRW", "USD");
    private static final Set<String> ORDER_STATUSES = Set.of("OPEN", "CLOSED");

    public record StockItem(String symbol, String name, String market, boolean commonShare) {}

    public record SearchResponse(List<StockItem> results) {}

    public record QuoteItem(String symbol, String lastPrice, String currency, String timestamp) {}

    public record QuoteResponse(List<QuoteItem> quotes) {}

    public record BuyingPowerResponse(String currency, String cash) {}

    public record OrderRow(
            String orderId,
            String symbol,
            String side,
            String orderType,
            String status,
            String price,
            String quantity,
            String currency,
            String orderedAt) {}

    public record OrdersResponse(String status, List<OrderRow> orders) {}

    private final StockSearchService stocks;
    private final QuoteService quotes;
    private final AccountQueryService account;

    public QueryController(StockSearchService stocks, QuoteService quotes, AccountQueryService account) {
        this.stocks = stocks;
        this.quotes = quotes;
        this.account = account;
    }

    @GetMapping("/stocks/search")
    public SearchResponse search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String limit) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty() || q.length() > MAX_QUERY_LENGTH) {
            throw new InvalidInputException("검색어는 1~" + MAX_QUERY_LENGTH + "자로 입력하세요.");
        }
        String m = optionalEnum(market, MARKETS, "시장은 KR 또는 US 만 쓸 수 있습니다.");
        List<StockMatch> found = stocks.search(q, m, parseLimit(limit));
        return new SearchResponse(found.stream()
                .map(s -> new StockItem(s.symbol(), s.name(), s.market(), s.commonShare()))
                .toList());
    }

    @GetMapping("/quote")
    public QuoteResponse quote(@RequestParam(required = false) String symbol) {
        List<Quote> found = quotes.quotes(normalizeSymbols(symbol));
        return new QuoteResponse(found.stream()
                .map(q -> new QuoteItem(q.symbol(), plain(q.lastPrice()), q.currency(), q.timestamp()))
                .toList());
    }

    @GetMapping("/buying-power")
    public BuyingPowerResponse buyingPower(@RequestParam(required = false) String currency) {
        String c = optionalEnum(currency, CURRENCIES, "통화는 KRW 또는 USD 만 쓸 수 있습니다.");
        BuyingPower bp = account.buyingPower(c);
        return new BuyingPowerResponse(bp.currency(), plain(bp.cash()));
    }

    @GetMapping("/orders")
    public OrdersResponse orders(@RequestParam(required = false) String status) {
        String s = optionalEnum(status, ORDER_STATUSES, "주문 상태는 OPEN 또는 CLOSED 만 쓸 수 있습니다.");
        String effective = s == null ? AccountQueryService.DEFAULT_ORDER_STATUS : s;
        List<OrderItem> found = account.orders(effective);
        return new OrdersResponse(
                effective,
                found.stream()
                        .map(o -> new OrderRow(
                                o.orderId(),
                                o.symbol(),
                                o.side(),
                                o.orderType(),
                                o.status(),
                                plain(o.price()),
                                plain(o.quantity()),
                                o.currency(),
                                o.orderedAt()))
                        .toList());
    }

    /** 쉼표로 나눈 종목 코드를 검증하고 공백 없이 대문자로 다시 잇는다. */
    static String normalizeSymbols(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new InvalidInputException("종목 코드를 입력하세요.");
        }
        List<String> parts =
                Arrays.stream(symbol.split(",", -1)).map(String::strip).toList();
        if (parts.size() > MAX_SYMBOLS) {
            throw new InvalidInputException("종목 코드는 한 번에 " + MAX_SYMBOLS + "개까지 조회할 수 있습니다.");
        }
        for (String p : parts) {
            if (!SYMBOL.matcher(p).matches()) {
                throw new InvalidInputException("종목 코드 형식이 올바르지 않습니다. 예: 005930, AAPL");
            }
        }
        return String.join(",", parts).toUpperCase(Locale.ROOT);
    }

    private static Integer parseLimit(String limit) {
        if (limit == null || limit.isBlank()) {
            return null;
        }
        String v = limit.strip();
        if (!LIMIT.matcher(v).matches() || Integer.parseInt(v) < 1 || Integer.parseInt(v) > MAX_LIMIT) {
            throw new InvalidInputException("최대 결과 수는 1~" + MAX_LIMIT + " 사이로 지정하세요.");
        }
        return Integer.parseInt(v);
    }

    private static String optionalEnum(String value, Set<String> allowed, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw new InvalidInputException(message);
        }
        return v;
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
