package io.github.psung616.tossmcp.order;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.OrderDetailDto;
import io.github.psung616.tossmcp.client.dto.OrderModifyRequest;
import io.github.psung616.tossmcp.client.dto.OrderOperationResult;
import io.github.psung616.tossmcp.client.dto.PriceDto;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.config.TossProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 주문 취소·정정 (P4-5·P4-6). 신규 주문과 같은 2단계: 미리보기(주문 상태 확인, 정정은 가드레일 재검사) → 1회용 changeId(만료 = previewTtl) → 실행.
 * DRY_RUN 이면 토스 API 를 부르지 않고 감사로그만 남긴다. 모든 취소·정정은 이 서비스를 통해서만 나간다(ArchUnit).
 *
 * <ul>
 *   <li>취소·정정 가능 상태: PENDING, PARTIAL_FILLED. 체결 완료·취소·거부·처리 중(PENDING_*)·REPLACED(이미 정정돼 새 주문이 있음)·모르는 상태는 거부
 *   <li>취소는 위험을 줄이는 동작이라 가드레일(한도·킬 스위치·장 시간)을 적용하지 않는다 — 킬 스위치가 켜져 있어도 미체결을 거둘 수 있어야 한다
 *   <li>정정은 정정 후 수량·가격으로 {@link GuardrailService} 를 다시 돌린다(보수적: 일일 횟수·누적 금액 검사도 새 주문처럼 적용). 실행 직전에 한 번 더 검사
 *   <li>KR 정정은 quantity 필수(양의 정수), US 정정은 quantity 를 보내지 않는다(가격만). 1억 이상 확인 플래그는 보내지 않는다(절대 원칙 7)
 * </ul>
 */
@Service
public class OrderChangeService {

    static final Set<String> CHANGEABLE = Set.of("PENDING", "PARTIAL_FILLED");

    public enum Kind {
        CANCEL,
        MODIFY
    }

    /**
     * 취소·정정 미리보기. 취소면 원주문 내용, 정정이면 정정 후 내용. quantity 는 US 정정이면 원주문 수량(보내지 않음, 금액 추정용).
     *
     * @param estimatedAmount 거래 통화 기준 예상 금액 (취소면 null)
     */
    public record ChangePreview(
            String changeId,
            Kind kind,
            String orderId,
            String symbol,
            String side,
            String status,
            String orderType,
            BigDecimal quantity,
            BigDecimal price,
            String currency,
            BigDecimal estimatedAmount,
            Instant expiresAt) {}

    /** 미리보기 결과. 거부면 preview 가 null 이고 violations 에 이유. */
    public record ChangePreviewResult(ChangePreview preview, List<String> violations, boolean dryRun) {

        public boolean allowed() {
            return preview != null;
        }
    }

    /** 실행 결과. DRY_RUN 이면 newOrderId 는 null. */
    public record ChangeResult(ChangePreview preview, boolean dryRun, String newOrderId) {}

    private final TossApi tossApi;
    private final GuardrailService guardrail;
    private final TossProperties properties;
    private final AuditLogger audit;
    private final Clock clock;
    private final Map<String, ChangePreview> pending = new ConcurrentHashMap<>();

    public OrderChangeService(
            TossApi tossApi, GuardrailService guardrail, TossProperties properties, AuditLogger audit, Clock clock) {
        this.tossApi = tossApi;
        this.guardrail = guardrail;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    public ChangePreviewResult previewCancel(String orderId) {
        OrderDetailDto o = load(orderId);
        String refusal = refusal(o, "취소");
        if (refusal != null) {
            return new ChangePreviewResult(null, List.of(refusal), properties.dryRun());
        }
        ChangePreview p = save(new ChangePreview(
                null,
                Kind.CANCEL,
                o.orderId(),
                o.symbol(),
                o.side(),
                o.status(),
                o.orderType(),
                o.quantity(),
                o.price(),
                o.currency(),
                null,
                null));
        audit.change("cancel-preview", properties.dryRun(), p, List.of(), null, null, null);
        return new ChangePreviewResult(p, List.of(), properties.dryRun());
    }

    /**
     * @param quantity KR: 필수(양의 정수). US: null 이어야 한다(가격만 정정)
     * @param price LIMIT 이면 필수, MARKET 이면 null
     */
    public ChangePreviewResult previewModify(String orderId, OrderType type, Long quantity, BigDecimal price) {
        if (type == null) {
            throw new IllegalArgumentException("정정할 주문 유형(LIMIT/MARKET)을 지정하세요.");
        }
        if (type == OrderType.LIMIT && (price == null || price.signum() <= 0)) {
            throw new IllegalArgumentException("지정가 정정은 0 보다 큰 가격이 필요합니다.");
        }
        if (type == OrderType.MARKET && price != null) {
            throw new IllegalArgumentException("시장가 정정에는 가격을 넣지 않습니다.");
        }
        OrderDetailDto o = load(orderId);
        String refusal = refusal(o, "정정");
        if (refusal != null) {
            return new ChangePreviewResult(null, List.of(refusal), properties.dryRun());
        }
        boolean us = "USD".equals(o.currency());
        if (us && quantity != null) {
            throw new IllegalArgumentException("미국 주식은 수량을 정정할 수 없습니다(가격만). 수량을 바꾸려면 취소 후 새로 주문하세요.");
        }
        if (!us && (quantity == null || quantity <= 0)) {
            throw new IllegalArgumentException("국내 주식 정정은 수량(양의 정수)이 필요합니다.");
        }
        long qty;
        try {
            qty = us ? o.quantity().longValueExact() : quantity;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("소수점 수량 주문은 정정할 수 없습니다. 취소 후 새로 주문하세요.");
        }
        BigDecimal unit = type == OrderType.LIMIT ? price : currentPrice(o.symbol());
        BigDecimal estimated = unit.multiply(BigDecimal.valueOf(qty));

        OrderCommand command = new OrderCommand(o.symbol(), OrderSide.valueOf(o.side()), type, qty, price);
        GuardrailResult result = guardrail.evaluate(command, o.currency(), estimated);
        ChangePreview candidate = new ChangePreview(
                null,
                Kind.MODIFY,
                o.orderId(),
                o.symbol(),
                o.side(),
                o.status(),
                type.name(),
                BigDecimal.valueOf(qty),
                price,
                o.currency(),
                estimated,
                null);
        if (!result.allowed()) {
            audit.change("modify-reject", properties.dryRun(), candidate, result.violations(), null, null, null);
            return new ChangePreviewResult(null, result.violations(), properties.dryRun());
        }
        ChangePreview p = save(candidate);
        audit.change("modify-preview", properties.dryRun(), p, List.of(), null, null, null);
        return new ChangePreviewResult(p, List.of(), properties.dryRun());
    }

    /** 취소·정정 실행. changeId 는 1회용 — 없거나 만료·사용됐으면 거부. */
    public synchronized ChangeResult execute(String changeId) {
        ChangePreview p = changeId == null ? null : pending.remove(changeId);
        if (p == null || clock.instant().isAfter(p.expiresAt())) {
            throw new GuardrailViolationException("changeId 가 없거나 만료되었거나 이미 사용되었습니다. 다시 미리보기 하세요.");
        }
        String event = p.kind() == Kind.CANCEL ? "cancel" : "modify";
        if (p.kind() == Kind.MODIFY) {
            // 미리보기 이후 킬 스위치·한도·장 시간이 바뀌었을 수 있다.
            OrderCommand command = new OrderCommand(
                    p.symbol(),
                    OrderSide.valueOf(p.side()),
                    OrderType.valueOf(p.orderType()),
                    p.quantity().longValueExact(),
                    p.price());
            GuardrailResult result = guardrail.evaluate(command, p.currency(), p.estimatedAmount());
            if (!result.allowed()) {
                audit.change("modify-reject", properties.dryRun(), p, result.violations(), null, null, null);
                throw new GuardrailViolationException(result.violations());
            }
        }
        if (properties.dryRun()) {
            audit.change(event, true, p, List.of(), null, null, "DRY_RUN");
            return new ChangeResult(p, true, null);
        }
        try {
            OrderOperationResult r = p.kind() == Kind.CANCEL
                    ? tossApi.cancelOrder(p.orderId())
                    : tossApi.modifyOrder(p.orderId(), toModifyRequest(p));
            String newOrderId = r == null ? null : r.orderId();
            audit.change(event, false, p, List.of(), newOrderId, null, "OK");
            return new ChangeResult(p, false, newOrderId);
        } catch (TossApiException e) {
            audit.change(event, false, p, List.of(), null, e.requestId(), e.code());
            throw e;
        }
    }

    static OrderModifyRequest toModifyRequest(ChangePreview p) {
        boolean us = "USD".equals(p.currency());
        return new OrderModifyRequest(
                p.orderType(),
                us ? null : p.quantity().toPlainString(),
                p.price() == null ? null : p.price().toPlainString());
    }

    private OrderDetailDto load(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("주문 ID 를 입력하세요. list_orders 로 확인할 수 있습니다.");
        }
        return tossApi.getOrder(orderId.strip());
    }

    private static String refusal(OrderDetailDto o, String action) {
        if (o.status() == null || !CHANGEABLE.contains(o.status())) {
            return "이 주문은 %s할 수 없는 상태입니다 (상태: %s). 미체결(PENDING)·부분 체결(PARTIAL_FILLED) 주문만 %s할 수 있습니다."
                    .formatted(action, o.status(), action);
        }
        return null;
    }

    private BigDecimal currentPrice(String symbol) {
        return tossApi.getPrices(symbol).stream()
                .findFirst()
                .map(PriceDto::lastPrice)
                .orElseThrow(() -> new TossApiException(404, "stock-not-found", "시세가 없습니다.", null));
    }

    private ChangePreview save(ChangePreview p) {
        Instant now = clock.instant();
        pending.values().removeIf(old -> now.isAfter(old.expiresAt()));
        ChangePreview stored = new ChangePreview(
                UUID.randomUUID().toString(),
                p.kind(),
                p.orderId(),
                p.symbol(),
                p.side(),
                p.status(),
                p.orderType(),
                p.quantity(),
                p.price(),
                p.currency(),
                p.estimatedAmount(),
                now.plus(properties.previewTtl()));
        pending.put(stored.changeId(), stored);
        return stored;
    }
}
