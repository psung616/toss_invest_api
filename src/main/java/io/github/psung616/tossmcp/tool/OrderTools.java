package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.order.OrderChangeService;
import io.github.psung616.tossmcp.order.OrderChangeService.ChangePreview;
import io.github.psung616.tossmcp.order.OrderChangeService.ChangePreviewResult;
import io.github.psung616.tossmcp.order.OrderChangeService.ChangeResult;
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
 * <p>P4-5·P4-6: preview_cancel / cancel_order, preview_modify / modify_order — 같은 2단계(미리보기 → 사용자 동의 → 실행).
 */
@Component
@ConditionalOnProperty(prefix = "tossinvest", name = "enable-orders", havingValue = "true")
public class OrderTools {

    private final OrderService orderService;
    private final OrderChangeService changes;

    public OrderTools(OrderService orderService, OrderChangeService changes) {
        this.orderService = orderService;
        this.changes = changes;
    }

    @McpTool(name = "preview_cancel", description = """
                    미체결 주문을 취소하기 전에 반드시 먼저 호출합니다. 실제 취소는 하지 않고 대상 주문과 changeId 를 돌려줍니다.
                    결과를 사용자에게 보여주고, 사용자가 명시적으로 동의한 경우에만 cancel_order 를 호출하세요. orderId 는 list_orders 로 확인합니다.""", annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String previewCancel(
            @McpToolParam(description = "취소할 주문 ID (list_orders 결과)", required = true) String orderId) {
        return ToolErrors.handle(() -> describe(changes.previewCancel(orderId), "cancel_order"));
    }

    @McpTool(
            name = "cancel_order",
            description = "preview_cancel 에서 받은 changeId 로 주문을 취소합니다. 반드시 사용자 동의 후에만 호출하세요.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String cancelOrder(
            @McpToolParam(description = "preview_cancel 이 발급한 changeId", required = true) String changeId) {
        return ToolErrors.handle(() -> executed(changes.execute(changeId)));
    }

    @McpTool(name = "preview_modify", description = """
                    미체결 주문의 가격·수량을 정정하기 전에 반드시 먼저 호출합니다. 실제 정정은 하지 않고 정정 후 내용·가드레일 검사 결과와 changeId 를 돌려줍니다.
                    국내 주식은 수량이 필수, 미국 주식은 가격만 정정할 수 있습니다(수량 생략).
                    결과를 사용자에게 보여주고, 사용자가 명시적으로 동의한 경우에만 modify_order 를 호출하세요.""", annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String previewModify(
            @McpToolParam(description = "정정할 주문 ID (list_orders 결과)", required = true) String orderId,
            @McpToolParam(description = "LIMIT(지정가) 또는 MARKET(시장가)", required = true) OrderType orderType,
            @McpToolParam(description = "정정 후 수량 (국내 주식 필수, 미국 주식은 생략)", required = false) Long quantity,
            @McpToolParam(description = "정정 후 지정가. 시장가면 생략", required = false) BigDecimal limitPrice) {
        return ToolErrors.handle(
                () -> describe(changes.previewModify(orderId, orderType, quantity, limitPrice), "modify_order"));
    }

    @McpTool(
            name = "modify_order",
            description = "preview_modify 에서 받은 changeId 로 주문을 정정합니다. 반드시 사용자 동의 후에만 호출하세요.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String modifyOrder(
            @McpToolParam(description = "preview_modify 가 발급한 changeId", required = true) String changeId) {
        return ToolErrors.handle(() -> executed(changes.execute(changeId)));
    }

    static String describe(ChangePreviewResult result, String nextTool) {
        String prefix = result.dryRun() ? "[DRY RUN] " : "";
        if (!result.allowed()) {
            return prefix + "할 수 없습니다:\n- " + String.join("\n- ", result.violations());
        }
        ChangePreview p = result.preview();
        String action = p.kind() == OrderChangeService.Kind.CANCEL ? "취소" : "정정";
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append("주문 ").append(action).append(" 미리보기\n");
        sb.append("- 주문: %s %s %s (상태 %s)\n".formatted(p.orderId(), p.symbol(), p.side(), p.status()));
        if (p.kind() == OrderChangeService.Kind.CANCEL) {
            sb.append("- 취소 대상: %s 수량 %s 가격 %s %s\n"
                    .formatted(p.orderType(), plain(p.quantity()), plain(p.price()), p.currency()));
        } else {
            sb.append("- 정정 후: %s 수량 %s 가격 %s %s%s\n"
                    .formatted(
                            p.orderType(),
                            plain(p.quantity()),
                            p.price() == null ? "시장가" : plain(p.price()),
                            p.currency(),
                            "USD".equals(p.currency()) ? " (미국 주식은 수량 변경 없음)" : ""));
            sb.append("- 예상 금액: %s %s\n".formatted(plain(p.estimatedAmount()), p.currency()));
        }
        sb.append("- changeId: %s (만료: %s, 1회용)\n".formatted(p.changeId(), p.expiresAt()));
        sb.append("사용자에게 위 내용을 확인받은 뒤 ").append(nextTool).append(" 에 changeId 를 전달하세요.");
        return sb.toString();
    }

    static String executed(ChangeResult r) {
        ChangePreview p = r.preview();
        String action = p.kind() == OrderChangeService.Kind.CANCEL ? "취소" : "정정";
        if (r.dryRun()) {
            return "[DRY RUN] 실제 %s 요청은 전송되지 않았습니다. 주문 %s (%s). 감사로그에 기록했습니다."
                    .formatted(action, p.orderId(), p.symbol());
        }
        return "주문 %s 요청 완료. 새 주문번호: %s (원주문 %s, %s). list_orders 로 상태를 확인하세요."
                .formatted(action, r.newOrderId(), p.orderId(), p.symbol());
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.toPlainString();
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
