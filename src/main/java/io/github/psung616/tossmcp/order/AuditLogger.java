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
