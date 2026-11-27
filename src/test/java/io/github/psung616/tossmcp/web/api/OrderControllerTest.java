package io.github.psung616.tossmcp.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.order.DailyCounterStore;
import io.github.psung616.tossmcp.order.GuardrailResult;
import io.github.psung616.tossmcp.order.GuardrailViolationException;
import io.github.psung616.tossmcp.order.OrderCommand;
import io.github.psung616.tossmcp.order.OrderService;
import io.github.psung616.tossmcp.order.OrderService.PlaceResult;
import io.github.psung616.tossmcp.order.OrderService.PreviewResult;
import io.github.psung616.tossmcp.order.OrderSide;
import io.github.psung616.tossmcp.order.OrderType;
import io.github.psung616.tossmcp.order.PreviewStore.Preview;
import io.github.psung616.tossmcp.service.AppStatusService;
import io.github.psung616.tossmcp.service.AppStatusService.AppStatus;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * P9-8 주문 REST (WEB_SECURITY WS-ORD-1~5). OrderService 는 가짜 — 실제 가드레일·DRY_RUN 동작은 OrderService 쪽 테스트가 본다. 세션·JSON·커스텀 헤더 검사는
 * LocalAccessFilterTest. 값은 모두 지어낸 것.
 */
class OrderControllerTest {

    private static final String PREVIEW_ID = "11111111-2222-3333-4444-555555555555";

    private final OrderService orders = mock(OrderService.class);
    private final AppStatusService appStatus = mock(AppStatusService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(appStatus.status()).thenReturn(new AppStatus("0.1.0", true, false, true, true));
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(orders))
                .setControllerAdvice(new ApiErrorAdvice(appStatus))
                .build();
    }

    private static Preview preview(OrderCommand c) throws Exception {
        Constructor<Preview> ctor = Preview.class.getDeclaredConstructor(
                String.class, OrderCommand.class, String.class, BigDecimal.class, BigDecimal.class, Instant.class);
        return ctor.newInstance(
                PREVIEW_ID,
                c,
                "KRW",
                c.limitPrice() == null ? new BigDecimal("71000") : c.limitPrice(),
                new BigDecimal("213000"),
                Instant.parse("2026-11-27T01:05:00Z"));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void registeredOnlyWhenOrdersEnabled() {
        ConditionalOnProperty c = OrderController.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(c).as("WS-ORD-1").isNotNull();
        assertThat(c.prefix()).isEqualTo("tossinvest");
        assertThat(c.name()).containsExactly("enable-orders");
        assertThat(c.havingValue()).isEqualTo("true");
        assertThat(c.matchIfMissing()).isFalse();
    }

    @Test
    void previewPassesValidatedCommandToSharedOrderService() throws Exception {
        when(orders.preview(any())).thenAnswer(inv -> {
            OrderCommand c = inv.getArgument(0);
            return new PreviewResult(preview(c), new GuardrailResult(true, List.of()), true);
        });

        postJson(
                        "/api/orders/preview",
                        "{\"symbol\":\" 005930 \",\"side\":\"buy\",\"orderType\":\"LIMIT\",\"quantity\":\"3\",\"limitPrice\":\"71000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.preview.previewId").value(PREVIEW_ID))
                .andExpect(jsonPath("$.preview.unitPrice").value("71000"))
                .andExpect(jsonPath("$.preview.estimatedKrw").value("213000"))
                .andExpect(jsonPath("$.preview.marketEstimate").value(false));

        ArgumentCaptor<OrderCommand> captor = ArgumentCaptor.forClass(OrderCommand.class);
        verify(orders).preview(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new OrderCommand("005930", OrderSide.BUY, OrderType.LIMIT, 3, new BigDecimal("71000")));
    }

    @Test
    void marketOrderIgnoresPriceAndIsMarkedAsEstimate() throws Exception {
        when(orders.preview(any()))
                .thenAnswer(inv ->
                        new PreviewResult(preview(inv.getArgument(0)), new GuardrailResult(true, List.of()), false));

        postJson(
                        "/api/orders/preview",
                        "{\"symbol\":\"005930\",\"side\":\"SELL\",\"orderType\":\"MARKET\",\"quantity\":\"1\",\"limitPrice\":\"abc\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview.marketEstimate").value(true))
                .andExpect(jsonPath("$.dryRun").value(false));
    }

    @Test
    void guardrailBlockedPreviewReturnsViolationsWithoutPreviewId() throws Exception {
        when(orders.preview(any()))
                .thenReturn(new PreviewResult(
                        null, new GuardrailResult(false, List.of("허용 종목이 아닙니다", "1회 주문 한도 초과")), true));

        postJson(
                        "/api/orders/preview",
                        "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"MARKET\",\"quantity\":\"1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.violations[1]").value("1회 주문 한도 초과"))
                .andExpect(jsonPath("$.preview").doesNotExist());
    }

    @Test
    void invalidPreviewInputIs400AndNeverReachesOrderService() throws Exception {
        String[] bad = {
            "{\"symbol\":\"<x>\",\"side\":\"BUY\",\"orderType\":\"MARKET\",\"quantity\":\"1\"}",
            "{\"symbol\":\"005930\",\"side\":\"HOLD\",\"orderType\":\"MARKET\",\"quantity\":\"1\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"STOP\",\"quantity\":\"1\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"MARKET\",\"quantity\":\"0\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"MARKET\",\"quantity\":\"1.5\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"MARKET\",\"quantity\":\"-1\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"MARKET\",\"quantity\":\"99999999\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"quantity\":\"1\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"quantity\":\"1\",\"limitPrice\":\"0\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"quantity\":\"1\",\"limitPrice\":\"1e9\"}",
            "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"MARKET\",\"quantity\": 1 ",
            "{}",
        };
        for (String json : bad) {
            postJson("/api/orders/preview", json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("invalid_input"));
        }
        verifyNoInteractions(orders);
    }

    @Test
    void placeDryRun() throws Exception {
        OrderCommand c = new OrderCommand("005930", OrderSide.BUY, OrderType.LIMIT, 3, new BigDecimal("71000"));
        when(orders.place(PREVIEW_ID)).thenReturn(new PlaceResult(preview(c), true, null));

        postJson("/api/orders/place", "{\"previewId\":\"" + PREVIEW_ID + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.orderId").doesNotExist())
                .andExpect(jsonPath("$.quantity").value("3"));
    }

    @Test
    void placeRejectedOrReusedPreviewIs409() throws Exception {
        when(orders.place(PREVIEW_ID))
                .thenThrow(new GuardrailViolationException("previewId 가 없거나 만료되었거나 이미 사용되었습니다. 다시 preview_order 하세요."));

        postJson("/api/orders/place", "{\"previewId\":\"" + PREVIEW_ID + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("order_rejected"));
    }

    @Test
    void placeRequiresUuidPreviewId() throws Exception {
        for (String json :
                new String[] {"{}", "{\"previewId\":\"\"}", "{\"previewId\":\"../../x\"}", "{\"previewId\":123}"}) {
            postJson("/api/orders/place", json).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(orders);
    }

    @Test
    void tossOrderErrorIsNormalizedAndCounterFileFailsClosed() throws Exception {
        when(orders.place(anyString())).thenThrow(new TossApiException(422, "insufficient-buying-power", "x", "req-5"));
        postJson("/api/orders/place", "{\"previewId\":\"" + PREVIEW_ID + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("insufficient-buying-power"))
                .andExpect(jsonPath("$.error.requestId").value("req-5"));

        DailyCounterStore.CounterFileException broken = counterFileException();
        when(orders.preview(any())).thenThrow(broken);
        postJson(
                        "/api/orders/preview",
                        "{\"symbol\":\"005930\",\"side\":\"BUY\",\"orderType\":\"MARKET\",\"quantity\":\"1\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("counter_unavailable"));
    }

    private static DailyCounterStore.CounterFileException counterFileException() throws Exception {
        Constructor<DailyCounterStore.CounterFileException> ctor =
                DailyCounterStore.CounterFileException.class.getDeclaredConstructor(String.class, Throwable.class);
        ctor.setAccessible(true);
        return ctor.newInstance("일일 주문 카운터 파일이 손상돼 주문 한도를 확인할 수 없습니다.", null);
    }
}
