package io.github.psung616.tossmcp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.OrderCreateRequest;
import io.github.psung616.tossmcp.client.dto.OrderCreateResult;
import io.github.psung616.tossmcp.client.dto.PriceDto;
import io.github.psung616.tossmcp.config.TossProperties;
import io.github.psung616.tossmcp.order.OrderService.PlaceResult;
import io.github.psung616.tossmcp.order.OrderService.PreviewResult;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * 주문 흐름 안전장치 (P4-3·P4-4 완료 기준 일부, P9-8 이 web 에서도 같은 서비스를 쓰므로 함께 고정): DRY_RUN 이면 주문 API 0회, previewId 1회용,
 * 확정 시 가드레일 재검사, confirmHighValueOrder 를 보내지 않음. TossApi·GuardrailService 는 가짜, 값은 지어낸 것.
 */
class OrderServiceTest {

    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final TossApi tossApi = mock(TossApi.class);
    private final GuardrailService guardrail = mock(GuardrailService.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private static TossProperties props(boolean dryRun) {
        return new TossProperties(
                true,
                dryRun,
                new BigDecimal("1000000"),
                new BigDecimal("3000000"),
                10,
                List.of("005930"),
                false,
                false,
                false,
                0,
                Duration.ofMinutes(5));
    }

    private OrderService service(boolean dryRun) {
        TossProperties p = props(dryRun);
        when(tossApi.getPrices("005930"))
                .thenReturn(
                        List.of(new PriceDto("005930", "2026-11-27T10:00:00+09:00", new BigDecimal("71000"), "KRW")));
        when(guardrail.evaluate(any(), anyString(), any())).thenReturn(new GuardrailResult(true, List.of()));
        return new OrderService(
                tossApi,
                guardrail,
                new PreviewStore(clock, p),
                new DailyCounterStore(clock, dir.resolve("daily-counters.json")),
                p,
                audit);
    }

    private static OrderCommand limitBuy() {
        return new OrderCommand("005930", OrderSide.BUY, OrderType.LIMIT, 3, new BigDecimal("70000"));
    }

    @Test
    void dryRunNeverCallsOrderApi() {
        OrderService service = service(true);
        PreviewResult preview = service.preview(limitBuy());

        PlaceResult placed = service.place(preview.preview().previewId());

        assertThat(placed.dryRun()).isTrue();
        assertThat(placed.orderId()).isNull();
        verify(tossApi, never()).createOrder(any());
        assertThat(preview.preview().estimatedKrw()).isEqualByComparingTo("210000");
    }

    @Test
    void previewIdIsSingleUse() {
        OrderService service = service(true);
        String id = service.preview(limitBuy()).preview().previewId();
        service.place(id);

        assertThatThrownBy(() -> service.place(id))
                .isInstanceOf(GuardrailViolationException.class)
                .hasMessageContaining("이미 사용");
    }

    @Test
    void placeReRunsGuardrailAndRefusesIfNowBlocked() {
        OrderService service = service(false);
        String id = service.preview(limitBuy()).preview().previewId();
        when(guardrail.evaluate(any(), anyString(), any()))
                .thenReturn(new GuardrailResult(false, List.of("킬 스위치가 켜져 있습니다")));

        assertThatThrownBy(() -> service.place(id)).isInstanceOf(GuardrailViolationException.class);
        verify(tossApi, never()).createOrder(any());
    }

    @Test
    void realOrderSendsPreviewIdAsClientOrderIdAndNoHighValueConfirmation() {
        OrderService service = service(false);
        when(tossApi.createOrder(any())).thenReturn(new OrderCreateResult("ord-1", null));
        String id = service.preview(limitBuy()).preview().previewId();

        PlaceResult placed = service.place(id);

        ArgumentCaptor<OrderCreateRequest> captor = ArgumentCaptor.forClass(OrderCreateRequest.class);
        verify(tossApi).createOrder(captor.capture());
        OrderCreateRequest sent = captor.getValue();
        assertThat(sent.clientOrderId()).isEqualTo(id);
        assertThat(sent.quantity()).isEqualTo("3");
        assertThat(sent.price()).isEqualTo("70000");
        assertThat(Arrays.stream(OrderCreateRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .as("절대 원칙 7: confirmHighValueOrder 필드 자체가 없다")
                .doesNotContain("confirmHighValueOrder");
        assertThat(placed.orderId()).isEqualTo("ord-1");
    }
}
