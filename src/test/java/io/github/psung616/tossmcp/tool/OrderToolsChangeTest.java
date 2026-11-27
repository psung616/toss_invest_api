package io.github.psung616.tossmcp.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.order.OrderChangeService.ChangePreview;
import io.github.psung616.tossmcp.order.OrderChangeService.ChangePreviewResult;
import io.github.psung616.tossmcp.order.OrderChangeService.ChangeResult;
import io.github.psung616.tossmcp.order.OrderChangeService.Kind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** P4-5·P4-6 도구 문구. 값은 지어낸 것. */
class OrderToolsChangeTest {

    private static ChangePreview preview(Kind kind, String currency, BigDecimal price, BigDecimal est) {
        return new ChangePreview(
                "chg-1",
                kind,
                "ord-1",
                "USD".equals(currency) ? "NVDA" : "005930",
                "BUY",
                "PENDING",
                "LIMIT",
                new BigDecimal("3"),
                price,
                currency,
                est,
                Instant.parse("2026-11-27T01:05:00Z"));
    }

    @Test
    void cancelPreviewTellsToAskUserBeforeCancelOrder() {
        String text = OrderTools.describe(
                new ChangePreviewResult(preview(Kind.CANCEL, "KRW", new BigDecimal("70000"), null), List.of(), true),
                "cancel_order");

        assertThat(text)
                .startsWith("[DRY RUN] 주문 취소 미리보기\n")
                .contains("- 주문: ord-1 005930 BUY (상태 PENDING)")
                .contains("- 취소 대상: LIMIT 수량 3 가격 70000 KRW")
                .contains("changeId: chg-1")
                .endsWith("cancel_order 에 changeId 를 전달하세요.");
    }

    @Test
    void usModifyPreviewNotesQuantityUnchanged() {
        String text = OrderTools.describe(
                new ChangePreviewResult(
                        preview(Kind.MODIFY, "USD", new BigDecimal("120.50"), new BigDecimal("361.50")),
                        List.of(),
                        false),
                "modify_order");

        assertThat(text)
                .startsWith("주문 정정 미리보기")
                .contains("- 정정 후: LIMIT 수량 3 가격 120.50 USD (미국 주식은 수량 변경 없음)")
                .contains("- 예상 금액: 361.50 USD");
    }

    @Test
    void refusalListsReasons() {
        assertThat(OrderTools.describe(new ChangePreviewResult(null, List.of("이 주문은 취소할 수 없는 상태입니다"), false), "x"))
                .isEqualTo("할 수 없습니다:\n- 이 주문은 취소할 수 없는 상태입니다");
    }

    @Test
    void executedMessages() {
        ChangePreview p = preview(Kind.CANCEL, "KRW", null, null);

        assertThat(OrderTools.executed(new ChangeResult(p, true, null))).startsWith("[DRY RUN] 실제 취소 요청은 전송되지 않았습니다.");
        assertThat(OrderTools.executed(new ChangeResult(p, false, "ord-1-c"))).contains("새 주문번호: ord-1-c");
    }
}
