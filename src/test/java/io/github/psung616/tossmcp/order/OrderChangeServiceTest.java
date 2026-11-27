package io.github.psung616.tossmcp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.OrderDetailDto;
import io.github.psung616.tossmcp.client.dto.OrderModifyRequest;
import io.github.psung616.tossmcp.client.dto.OrderOperationResult;
import io.github.psung616.tossmcp.client.dto.PriceDto;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.config.TossProperties;
import io.github.psung616.tossmcp.order.OrderChangeService.ChangePreviewResult;
import io.github.psung616.tossmcp.order.OrderChangeService.ChangeResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** P4-5 취소 · P4-6 정정. TossApi·GuardrailService 는 가짜, 값은 지어낸 것. 실제 API 를 부르지 않는다. */
class OrderChangeServiceTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-27T01:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final TossApi tossApi = mock(TossApi.class);
    private final GuardrailService guardrail = mock(GuardrailService.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private OrderChangeService service(boolean dryRun) {
        TossProperties p = new TossProperties(
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
        when(guardrail.evaluate(any(), anyString(), any())).thenReturn(new GuardrailResult(true, List.of()));
        return new OrderChangeService(tossApi, guardrail, p, audit, clock);
    }

    private void order(String id, String status, String currency, String qty) {
        when(tossApi.getOrder(id))
                .thenReturn(new OrderDetailDto(
                        id,
                        "USD".equals(currency) ? "NVDA" : "005930",
                        "BUY",
                        "LIMIT",
                        "DAY",
                        status,
                        new BigDecimal("70000"),
                        new BigDecimal(qty),
                        null,
                        currency,
                        "2026-11-27T09:10:00+09:00",
                        null,
                        null));
    }

    // ---- 취소 (P4-5)

    @Test
    void cancelDryRunNeverCallsApi() {
        order("ord-1", "PENDING", "KRW", "3");
        OrderChangeService s = service(true);

        ChangePreviewResult preview = s.previewCancel("ord-1");
        ChangeResult result = s.execute(preview.preview().changeId());

        assertThat(result.dryRun()).isTrue();
        verify(tossApi, never()).cancelOrder(anyString());
        verify(guardrail, never()).evaluate(any(), anyString(), any());
    }

    @Test
    void cancelRealCallsApiAndReturnsNewOrderId() {
        order("ord-1", "PARTIAL_FILLED", "KRW", "3");
        when(tossApi.cancelOrder("ord-1")).thenReturn(new OrderOperationResult("ord-1-c"));
        OrderChangeService s = service(false);

        ChangeResult result = s.execute(s.previewCancel("ord-1").preview().changeId());

        assertThat(result.newOrderId()).isEqualTo("ord-1-c");
        verify(audit).change(eq("cancel"), eq(false), any(), any(), eq("ord-1-c"), any(), eq("OK"));
    }

    @Test
    void filledCanceledOrUnknownStatusesAreRefused() {
        OrderChangeService s = service(false);
        for (String status : List.of("FILLED", "CANCELED", "REJECTED", "PENDING_CANCEL", "REPLACED", "SOMETHING_NEW")) {
            order("ord-x", status, "KRW", "1");
            ChangePreviewResult r = s.previewCancel("ord-x");
            assertThat(r.allowed()).as(status).isFalse();
            assertThat(r.violations()).singleElement().asString().contains(status);
        }
        verify(tossApi, never()).cancelOrder(anyString());
    }

    @Test
    void cancelRestrictedErrorFromTossIsPropagatedAndAudited() {
        order("ord-1", "PENDING", "KRW", "1");
        when(tossApi.cancelOrder("ord-1")).thenThrow(new TossApiException(409, "cancel-restricted", "x", "req-9"));
        OrderChangeService s = service(false);
        String id = s.previewCancel("ord-1").preview().changeId();

        assertThatThrownBy(() -> s.execute(id)).isInstanceOf(TossApiException.class);
        verify(audit).change(eq("cancel"), eq(false), any(), any(), eq(null), eq("req-9"), eq("cancel-restricted"));
    }

    @Test
    void changeIdIsSingleUseAndExpires() {
        order("ord-1", "PENDING", "KRW", "1");
        OrderChangeService s = service(true);
        String used = s.previewCancel("ord-1").preview().changeId();
        s.execute(used);
        assertThatThrownBy(() -> s.execute(used)).isInstanceOf(GuardrailViolationException.class);

        String expiring = s.previewCancel("ord-1").preview().changeId();
        clock.now = clock.now.plus(Duration.ofMinutes(5)).plusSeconds(1);
        assertThatThrownBy(() -> s.execute(expiring)).isInstanceOf(GuardrailViolationException.class);
        assertThatThrownBy(() -> s.execute(null)).isInstanceOf(GuardrailViolationException.class);
    }

    // ---- 정정 (P4-6)

    @Test
    void krModifyRunsGuardrailOnNewAmountAndSendsQuantity() {
        order("ord-1", "PENDING", "KRW", "3");
        when(tossApi.modifyOrder(eq("ord-1"), any())).thenReturn(new OrderOperationResult("ord-1-m"));
        OrderChangeService s = service(false);

        ChangePreviewResult preview = s.previewModify("ord-1", OrderType.LIMIT, 5L, new BigDecimal("69000"));
        assertThat(preview.preview().estimatedAmount()).isEqualByComparingTo("345000");
        ChangeResult result = s.execute(preview.preview().changeId());

        ArgumentCaptor<OrderCommand> command = ArgumentCaptor.forClass(OrderCommand.class);
        verify(guardrail, org.mockito.Mockito.times(2))
                .evaluate(command.capture(), eq("KRW"), eq(new BigDecimal("345000")));
        assertThat(command.getValue())
                .isEqualTo(new OrderCommand("005930", OrderSide.BUY, OrderType.LIMIT, 5, new BigDecimal("69000")));
        ArgumentCaptor<OrderModifyRequest> sent = ArgumentCaptor.forClass(OrderModifyRequest.class);
        verify(tossApi).modifyOrder(eq("ord-1"), sent.capture());
        assertThat(sent.getValue()).isEqualTo(new OrderModifyRequest("LIMIT", "5", "69000"));
        assertThat(result.newOrderId()).isEqualTo("ord-1-m");
    }

    @Test
    void krModifyRequiresQuantityAndUsModifyRefusesQuantity() {
        order("ord-kr", "PENDING", "KRW", "3");
        order("ord-us", "PENDING", "USD", "2");
        OrderChangeService s = service(false);

        assertThatThrownBy(() -> s.previewModify("ord-kr", OrderType.LIMIT, null, new BigDecimal("1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량");
        assertThatThrownBy(() -> s.previewModify("ord-us", OrderType.LIMIT, 1L, new BigDecimal("1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미국");
        assertThatThrownBy(() -> s.previewModify("ord-kr", OrderType.LIMIT, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.previewModify("ord-kr", OrderType.MARKET, 1L, new BigDecimal("1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usModifyRequestOmitsQuantityAndMarketOmitsPrice() throws Exception {
        JsonMapper json = JsonMapper.builder().build();

        assertThat(json.writeValueAsString(new OrderModifyRequest("LIMIT", null, "120.5")))
                .isEqualTo("{\"orderType\":\"LIMIT\",\"price\":\"120.5\"}");
        assertThat(json.writeValueAsString(new OrderModifyRequest("MARKET", "3", null)))
                .isEqualTo("{\"orderType\":\"MARKET\",\"quantity\":\"3\"}")
                .doesNotContain("confirmHighValueOrder");
    }

    @Test
    void marketModifyEstimatesWithCurrentPrice() {
        order("ord-1", "PENDING", "KRW", "3");
        when(tossApi.getPrices("005930"))
                .thenReturn(List.of(new PriceDto("005930", "t", new BigDecimal("71000"), "KRW")));
        OrderChangeService s = service(true);

        ChangePreviewResult r = s.previewModify("ord-1", OrderType.MARKET, 2L, null);

        assertThat(r.preview().estimatedAmount()).isEqualByComparingTo("142000");
        assertThat(r.preview().price()).isNull();
    }

    @Test
    void modifyBlockedByGuardrailAtPreviewOrExecute() {
        order("ord-1", "PENDING", "KRW", "3");
        OrderChangeService s = service(false);
        when(guardrail.evaluate(any(), anyString(), any()))
                .thenReturn(new GuardrailResult(false, List.of("1회 주문 한도 초과")));

        ChangePreviewResult blocked = s.previewModify("ord-1", OrderType.LIMIT, 100L, new BigDecimal("70000"));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.violations()).containsExactly("1회 주문 한도 초과");

        when(guardrail.evaluate(any(), anyString(), any())).thenReturn(new GuardrailResult(true, List.of()));
        String id = s.previewModify("ord-1", OrderType.LIMIT, 1L, new BigDecimal("70000"))
                .preview()
                .changeId();
        when(guardrail.evaluate(any(), anyString(), any()))
                .thenReturn(new GuardrailResult(false, List.of("킬 스위치가 켜져 있습니다")));
        assertThatThrownBy(() -> s.execute(id)).isInstanceOf(GuardrailViolationException.class);
        verify(tossApi, never()).modifyOrder(anyString(), any());
    }
}
