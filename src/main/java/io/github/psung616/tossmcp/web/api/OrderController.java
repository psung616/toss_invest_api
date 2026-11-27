package io.github.psung616.tossmcp.web.api;

import io.github.psung616.tossmcp.order.OrderCommand;
import io.github.psung616.tossmcp.order.OrderService;
import io.github.psung616.tossmcp.order.OrderService.PlaceResult;
import io.github.psung616.tossmcp.order.OrderService.PreviewResult;
import io.github.psung616.tossmcp.order.OrderSide;
import io.github.psung616.tossmcp.order.OrderType;
import io.github.psung616.tossmcp.order.PreviewStore.Preview;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 REST (P9-8, WEB_SECURITY WS-ORD, ARCHITECTURE §12.4). 👤 착수 승인 2026-11-27.
 *
 * <ul>
 *   <li>WS-ORD-1: {@code TOSSINVEST_ENABLE_ORDERS=true} 일 때만 빈 등록 — 아니면 엔드포인트가 없다(404)
 *   <li>WS-ORD-2: MCP 와 <b>같은</b> {@link OrderService} — 미리보기(가드레일·1회용 previewId 5분) → 확정(previewId 재검증·가드레일 재실행·DRY_RUN·감사로그)
 *   <li>WS-ORD-3: 화면은 미리보기를 보여 주고 사용자가 [주문 확정]을 눌러야만 place 를 부른다. 여기서 자동 확정하지 않는다
 *   <li>WS-ORD-4: 1억원 이상 주문의 confirmHighValueOrder 를 설정하지 않는다(가드레일이 1억 미만으로 막는다 — 절대 원칙 7)
 *   <li>WS-ORD-5: JSON + {@code X-Tossinvest-Local} + 4KB + 세션은 {@code LocalAccessFilter} 가 먼저 검사한다
 * </ul>
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "tossinvest", name = "enable-orders", havingValue = "true")
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController {

    static final long MAX_QUANTITY = 1_000_000L;
    private static final Pattern SYMBOL = Pattern.compile("[A-Za-z0-9.\\-]{1,20}");
    private static final Pattern QUANTITY = Pattern.compile("[1-9]\\d{0,6}");
    private static final Pattern PRICE = Pattern.compile("\\d{1,12}(\\.\\d{1,4})?");
    private static final Pattern PREVIEW_ID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** 수량·가격은 문자열로 받는다(JSON 숫자의 부동소수점 해석을 피함). */
    public record PreviewRequest(String symbol, String side, String orderType, String quantity, String limitPrice) {}

    public record PlaceRequest(String previewId) {}

    public record PreviewView(
            String previewId,
            String symbol,
            String side,
            String orderType,
            String quantity,
            String unitPrice,
            String currency,
            boolean marketEstimate,
            String estimatedKrw,
            String expiresAt) {}

    public record PreviewResponse(boolean allowed, boolean dryRun, List<String> violations, PreviewView preview) {}

    public record PlaceResponse(
            boolean dryRun, String orderId, String symbol, String side, String quantity, String estimatedKrw) {}

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping(path = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PreviewResponse preview(@RequestBody PreviewRequest request) {
        OrderCommand command = toCommand(request);
        PreviewResult result = orders.preview(command);
        if (!result.guardrail().allowed()) {
            return new PreviewResponse(
                    false, result.dryRun(), result.guardrail().violations(), null);
        }
        Preview p = result.preview();
        return new PreviewResponse(
                true,
                result.dryRun(),
                List.of(),
                new PreviewView(
                        p.previewId(),
                        p.command().symbol(),
                        p.command().side().name(),
                        p.command().type().name(),
                        Long.toString(p.command().quantity()),
                        p.unitPrice().toPlainString(),
                        p.currency(),
                        p.command().type() == OrderType.MARKET,
                        p.estimatedKrw().toPlainString(),
                        p.expiresAt().toString()));
    }

    @PostMapping(path = "/place", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlaceResponse place(@RequestBody PlaceRequest request) {
        String previewId = request == null || request.previewId() == null
                ? ""
                : request.previewId().strip();
        if (!PREVIEW_ID.matcher(previewId).matches()) {
            throw new InvalidInputException("previewId 형식이 올바르지 않습니다. 다시 미리보기 하세요.");
        }
        PlaceResult result = orders.place(previewId);
        var c = result.preview().command();
        return new PlaceResponse(
                result.dryRun(),
                result.orderId(),
                c.symbol(),
                c.side().name(),
                Long.toString(c.quantity()),
                result.preview().estimatedKrw().toPlainString());
    }

    static OrderCommand toCommand(PreviewRequest r) {
        if (r == null) {
            throw new InvalidInputException("주문 내용을 입력하세요.");
        }
        String symbol = r.symbol() == null ? "" : r.symbol().strip();
        if (!SYMBOL.matcher(symbol).matches()) {
            throw new InvalidInputException("종목 코드 형식이 올바르지 않습니다. 예: 005930, AAPL");
        }
        OrderSide side = enumOf(OrderSide.class, r.side(), "구분은 BUY(매수) 또는 SELL(매도)만 쓸 수 있습니다.");
        OrderType type = enumOf(OrderType.class, r.orderType(), "주문 유형은 LIMIT(지정가) 또는 MARKET(시장가)만 쓸 수 있습니다.");
        String qty = r.quantity() == null ? "" : r.quantity().strip();
        if (!QUANTITY.matcher(qty).matches() || Long.parseLong(qty) > MAX_QUANTITY) {
            throw new InvalidInputException("수량은 1~" + MAX_QUANTITY + " 사이의 정수로 입력하세요.");
        }
        BigDecimal price = null;
        if (type == OrderType.LIMIT) {
            String p = r.limitPrice() == null ? "" : r.limitPrice().strip();
            if (!PRICE.matcher(p).matches() || new BigDecimal(p).signum() <= 0) {
                throw new InvalidInputException("지정가는 0 보다 큰 숫자(소수 4자리까지)로 입력하세요.");
            }
            price = new BigDecimal(p);
        }
        return new OrderCommand(symbol, side, type, Long.parseLong(qty), price);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value, String message) {
        if (value == null) {
            throw new InvalidInputException(message);
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(message);
        }
    }
}
