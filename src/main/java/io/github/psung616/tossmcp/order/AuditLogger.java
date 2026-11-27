package io.github.psung616.tossmcp.order;

import io.github.psung616.tossmcp.order.PreviewStore.Preview;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 이벤트를 사용자 폴더의 audit.jsonl 에 한 줄씩 남긴다 (ARCHITECTURE §9).
 * 키·토큰·accountSeq 는 절대 기록하지 않는다.
 */
@Component
public class AuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditLogger(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void preview(Preview preview, boolean dryRun, GuardrailResult guardrail) {
        write(
                "preview",
                dryRun,
                preview.command(),
                preview.estimatedKrw().toPlainString(),
                guardrail.violations(),
                null,
                null);
    }

    public void reject(OrderCommand command, boolean dryRun, List<String> violations) {
        write("reject", dryRun, command, null, violations, null, null);
    }

    public void place(Preview preview, boolean dryRun, String resultCode) {
        write("place", dryRun, preview.command(), preview.estimatedKrw().toPlainString(), List.of(), null, resultCode);
    }

    public void placeFailed(Preview preview, String requestId, String errorCode) {
        write(
                "place",
                false,
                preview.command(),
                preview.estimatedKrw().toPlainString(),
                List.of(),
                requestId,
                errorCode);
    }

    /**
     * 취소·정정 이벤트 (P4-5·P4-6). event: {@code cancel-preview|cancel|modify-preview|modify|modify-reject}. orderId 는 토스가 준 불투명 식별자
     * (계좌·키 아님). newOrderId 는 실행 결과로 새로 발급된 식별자.
     */
    public void change(
            String event,
            boolean dryRun,
            OrderChangeService.ChangePreview p,
            List<String> guardrail,
            String newOrderId,
            String requestId,
            String resultCode) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ts", clock.instant().toString());
        line.put("event", event);
        line.put("dryRun", dryRun);
        line.put("orderId", p.orderId());
        line.put("symbol", p.symbol());
        line.put("side", p.side());
        line.put("orderType", p.orderType());
        line.put("qty", p.quantity() == null ? null : p.quantity().toPlainString());
        line.put("price", p.price() == null ? null : p.price().toPlainString());
        line.put(
                "estAmountKrw",
                p.estimatedAmount() == null ? null : p.estimatedAmount().toPlainString());
        line.put("guardrail", guardrail);
        line.put("newOrderId", newOrderId);
        line.put("requestId", requestId);
        line.put("resultCode", resultCode);
        audit.info(jsonMapper.writeValueAsString(line));
    }

    private void write(
            String event,
            boolean dryRun,
            OrderCommand command,
            String estAmountKrw,
            List<String> guardrail,
            String requestId,
            String resultCode) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ts", clock.instant().toString());
        line.put("event", event);
        line.put("dryRun", dryRun);
        line.put("symbol", command.symbol());
        line.put("side", command.side().name());
        line.put("qty", Long.toString(command.quantity()));
        line.put(
                "price",
                command.limitPrice() == null ? null : command.limitPrice().toPlainString());
        line.put("estAmountKrw", estAmountKrw);
        line.put("guardrail", guardrail);
        line.put("requestId", requestId);
        line.put("resultCode", resultCode);
        audit.info(jsonMapper.writeValueAsString(line));
    }
}
