package io.github.psung616.tossmcp.order;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.OrderCreateRequest;
import io.github.psung616.tossmcp.client.dto.OrderCreateResult;
import io.github.psung616.tossmcp.client.dto.PriceDto;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.config.TossProperties;
import io.github.psung616.tossmcp.order.PreviewStore.Preview;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * 주문 흐름: preview(검사 + previewId 발급) → place(previewId 재검증 + 실행).
 * 모든 주문은 이 서비스를 통해서만 나간다.
 *
 * TODO(P4-3): 상하한가 범위 검사, 수수료 포함 예상금액
 * TODO(P4-4): clientOrderId 형식을 스펙(P0-6)과 대조
 */
@Service
public class OrderService {

    private final TossApi tossApi;
    private final GuardrailService guardrail;
    private final PreviewStore previewStore;
    private final DailyCounterStore counters;
    private final TossProperties properties;
    private final AuditLogger audit;

    public OrderService(
            TossApi tossApi,
            GuardrailService guardrail,
            PreviewStore previewStore,
            DailyCounterStore counters,
            TossProperties properties,
            AuditLogger audit) {
        this.tossApi = tossApi;
        this.guardrail = guardrail;
        this.previewStore = previewStore;
        this.counters = counters;
        this.properties = properties;
        this.audit = audit;
    }

    public PreviewResult preview(OrderCommand command) {
        PriceDto price = tossApi.getPrices(command.symbol()).stream()
                .findFirst()
                .orElseThrow(() -> new TossApiException(404, "stock-not-found", "시세가 없습니다.", null));

        // 시장가는 현재가 기준 추정치라 실제 체결 금액과 다를 수 있다.
        BigDecimal unitPrice = command.type() == OrderType.LIMIT ? command.limitPrice() : price.lastPrice();
        BigDecimal estimated = unitPrice.multiply(BigDecimal.valueOf(command.quantity()));

        GuardrailResult result = guardrail.evaluate(command, price.currency(), estimated);
        if (!result.allowed()) {
            audit.reject(command, properties.dryRun(), result.violations());
            return new PreviewResult(null, result, properties.dryRun());
        }
        Preview preview = previewStore.save(command, price.currency(), unitPrice, estimated);
        audit.preview(preview, properties.dryRun(), result);
        return new PreviewResult(preview, result, properties.dryRun());
    }

    public synchronized PlaceResult place(String previewId) {
        Preview preview = previewStore
                .take(previewId)
                .orElseThrow(() ->
                        new GuardrailViolationException("previewId 가 없거나 만료되었거나 이미 사용되었습니다. 다시 preview_order 하세요."));

        // 미리보기 이후 킬 스위치·일일 한도가 바뀌었을 수 있으므로 다시 검사한다.
        GuardrailResult result = guardrail.evaluate(preview.command(), preview.currency(), preview.estimatedKrw());
        if (!result.allowed()) {
            audit.reject(preview.command(), properties.dryRun(), result.violations());
            throw new GuardrailViolationException(result.violations());
        }

        if (properties.dryRun()) {
            counters.record(true, preview.estimatedKrw());
            audit.place(preview, true, "DRY_RUN");
            return new PlaceResult(preview, true, null);
        }

        try {
            OrderCreateResult created = tossApi.createOrder(toRequest(preview));
            counters.record(false, preview.estimatedKrw());
            audit.place(preview, false, "OK");
            return new PlaceResult(preview, false, created == null ? null : created.orderId());
        } catch (TossApiException e) {
            audit.placeFailed(preview, e.requestId(), e.code());
            throw e;
        }
    }

    private static OrderCreateRequest toRequest(Preview preview) {
        OrderCommand c = preview.command();
        return new OrderCreateRequest(
                preview.previewId(),
                c.symbol(),
                c.side().name(),
                c.type().name(),
                Long.toString(c.quantity()),
                c.limitPrice() == null ? null : c.limitPrice().toPlainString());
    }

    public record PreviewResult(Preview preview, GuardrailResult guardrail, boolean dryRun) {}

    public record PlaceResult(Preview preview, boolean dryRun, String orderId) {}
}
