package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.order.OrderCommand;
import io.github.psung616.tossmcp.order.OrderService;
import io.github.psung616.tossmcp.order.OrderService.PlaceResult;
import io.github.psung616.tossmcp.order.OrderService.PreviewResult;
import io.github.psung616.tossmcp.order.OrderSide;
import io.github.psung616.tossmcp.order.OrderType;
import io.github.psung616.tossmcp.order.PreviewStore.Preview;
import java.math.BigDecimal;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 주문 도구. TOSSINVEST_ENABLE_ORDERS=true 일 때만 빈으로 등록된다 (false 면 도구 자체가 노출되지 않음).
 *
 * TODO(P4-5, P4-6): preview_cancel / cancel_order, preview_modify / modify_order
 */
@Component
@ConditionalOnProperty(prefix = "tossinvest", name = "enable-orders", havingValue = "true")
public class OrderTools {

    private final OrderService orderService;

    public OrderTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @McpTool(name = "preview_order", description = """
                    주문하기 전에 반드시 먼저 호출합니다. 실제 주문은 나가지 않고 검증 결과와 previewId 를 돌려줍니다.
                    결과를 사용자에게 그대로 보여주고, 사용자가 명시적으로 동의한 경우에만 place_order 를 호출하세요.""", annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String previewOrder(
            @McpToolParam(description = "종목 코드 (예: 005930)", required = true) String symbol,
            @McpToolParam(description = "BUY(매수) 또는 SELL(매도)", required = true) OrderSide side,
            @McpToolParam(description = "LIMIT(지정가) 또는 MARKET(시장가)", required = true) OrderType orderType,
            @McpToolParam(description = "주문 수량 (주, 양의 정수)", required = true) long quantity,
            @McpToolParam(description = "지정가 주문 가격. 시장가면 생략", required = false) BigDecimal limitPrice) {
        return ToolErrors.handle(() -> {
            PreviewResult result =
                    orderService.preview(new OrderCommand(symbol, side, orderType, quantity, limitPrice));
            String prefix = result.dryRun() ? "[DRY RUN] " : "";
            if (!result.guardrail().allowed()) {
                return prefix + "주문할 수 없습니다:\n- "
                        + String.join("\n- ", result.guardrail().violations());
            }
            Preview p = result.preview();
            var c = p.command();
            return """
                    %s주문 미리보기
                    - 종목: %s
                    - 구분: %s / %s
                    - 수량: %d주
                    - 단가: %s %s%s
                    - 예상 금액: %s원
                    - previewId: %s (만료: %s, 1회용)
                    사용자에게 위 내용을 확인받은 뒤 place_order 에 previewId 를 전달하세요.""".formatted(
                            prefix,
                            c.symbol(),
                            c.side(),
                            c.type(),
                            c.quantity(),
                            p.unitPrice().toPlainString(),
                            p.currency(),
                            c.type() == OrderType.MARKET ? " (현재가 기준 추정)" : "",
                            p.estimatedKrw().toPlainString(),
                            p.previewId(),
                            p.expiresAt());
        });
    }

    @McpTool(
            name = "place_order",
            description = "preview_order 에서 받은 previewId 로 주문을 실행합니다. 반드시 사용자 동의 후에만 호출하세요.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String placeOrder(
            @McpToolParam(description = "preview_order 가 발급한 previewId", required = true) String previewId) {
        return ToolErrors.handle(() -> {
            PlaceResult result = orderService.place(previewId);
            var c = result.preview().command();
            if (result.dryRun()) {
                return "[DRY RUN] 실제 주문은 전송되지 않았습니다. %s %s %d주 (예상 %s원). 감사로그에 기록했습니다."
                        .formatted(
                                c.symbol(),
                                c.side(),
                                c.quantity(),
                                result.preview().estimatedKrw().toPlainString());
            }
            return "주문 전송 완료. 주문번호: %s (%s %s %d주)".formatted(result.orderId(), c.symbol(), c.side(), c.quantity());
        });
    }
}
