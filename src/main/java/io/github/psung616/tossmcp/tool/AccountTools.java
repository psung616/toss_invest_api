package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.service.AccountQueryService;
import io.github.psung616.tossmcp.service.AccountQueryService.BuyingPower;
import io.github.psung616.tossmcp.service.AccountQueryService.OrderDetail;
import io.github.psung616.tossmcp.service.AccountQueryService.OrderItem;
import io.github.psung616.tossmcp.service.PortfolioService;
import io.github.psung616.tossmcp.service.PortfolioService.Holding;
import io.github.psung616.tossmcp.service.PortfolioService.Portfolio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 계좌 조회 도구. 조회는 {@code service} 가 하고, 여기서는 Claude 에게 보낼 문장으로만 바꾼다(P9-3 — web 과 같은 서비스 공유).
 *
 * <p>P3-4: get_portfolio, get_sellable_quantity, get_order 추가.
 */
@Component
public class AccountTools {

    private final AccountQueryService account;
    private final PortfolioService portfolio;

    public AccountTools(AccountQueryService account, PortfolioService portfolio) {
        this.account = account;
        this.portfolio = portfolio;
    }

    @McpTool(
            name = "get_portfolio",
            description = "보유 종목·평가금액·손익·비중과 합계를 확인할 때 사용합니다. 국내(KRW)와 미국(USD) 종목을 표준 환율로 원화 합산합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getPortfolio() {
        return ToolErrors.handle(() -> formatPortfolio(portfolio.portfolio()));
    }

    @McpTool(
            name = "get_sellable_quantity",
            description = "매도 전에 종목의 매도 가능 수량을 확인할 때 사용합니다. 입력은 종목 코드(symbol)입니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSellableQuantity(
            @McpToolParam(description = "종목 코드. 국내 6자리(예: 005930), 미국 티커(예: AAPL)", required = true) String symbol) {
        return ToolErrors.handle(() -> {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("종목 코드를 입력하세요.");
            }
            String s = symbol.strip();
            return "%s 매도 가능 수량: %s".formatted(s, plain(account.sellableQuantity(s)));
        });
    }

    @McpTool(
            name = "get_order",
            description = "주문 한 건의 상세(상태·체결 수량·평균 체결가·수수료)를 확인할 때 사용합니다. orderId 는 list_orders 결과의 첫 값입니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getOrder(@McpToolParam(description = "주문 ID (list_orders 결과에서 확인)", required = true) String orderId) {
        return ToolErrors.handle(() -> {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("주문 ID 를 입력하세요.");
            }
            OrderDetail o = account.order(orderId.strip());
            StringBuilder sb = new StringBuilder();
            sb.append("%s %s %s %s(%s) %s\n"
                    .formatted(o.orderId(), o.symbol(), o.side(), o.orderType(), o.timeInForce(), o.status()));
            sb.append("주문: 수량 %s 가격 %s %s".formatted(plain(o.quantity()), plain(o.price()), o.currency()));
            if (o.orderAmount() != null) {
                sb.append(" (금액 %s %s)".formatted(plain(o.orderAmount()), o.currency()));
            }
            sb.append("\n주문 시각: %s".formatted(dash(o.orderedAt())));
            if (o.canceledAt() != null) {
                sb.append("\n취소 시각: %s".formatted(o.canceledAt()));
            }
            sb.append("\n체결: 수량 %s 평균가 %s 금액 %s 수수료 %s 세금 %s"
                    .formatted(
                            plain(o.filledQuantity()),
                            plain(o.averageFilledPrice()),
                            plain(o.filledAmount()),
                            plain(o.commission()),
                            plain(o.tax())));
            sb.append("\n최근 체결 시각: %s, 결제일: %s".formatted(dash(o.filledAt()), dash(o.settlementDate())));
            return sb.toString();
        });
    }

    static String formatPortfolio(Portfolio p) {
        if (p.isEmpty()) {
            return "보유 종목이 없습니다.";
        }
        StringBuilder sb = new StringBuilder();
        if (p.usd() == null) {
            sb.append("보유 종목 %d개\n".formatted(p.holdings().size()));
        } else if (p.combinedUnavailable()) {
            sb.append("보유 종목 %d개 (환율을 받지 못해 원화 합산·비중은 생략)\n"
                    .formatted(p.holdings().size()));
        } else {
            sb.append("보유 종목 %d개 (표준 환율 1 USD = %s KRW 로 원화 합산)\n"
                    .formatted(p.holdings().size(), plain(p.usdKrwRate())));
        }
        if (p.totalValueKrw() != null) {
            sb.append("합계 평가금액: %s KRW".formatted(plain(p.totalValueKrw())));
            if (p.usd() != null) {
                sb.append(" (국내 %s KRW + 해외 %s USD)"
                        .formatted(plain(p.krw().value()), plain(p.usd().value())));
            }
            sb.append("\n합계 손익: %s KRW\n".formatted(signed(p.totalProfitKrw())));
        } else {
            sb.append("평가금액: 국내 %s KRW, 해외 %s USD\n"
                    .formatted(plain(p.krw().value()), plain(p.usd().value())));
            sb.append("손익: 국내 %s KRW, 해외 %s USD\n"
                    .formatted(signed(p.krw().profit()), signed(p.usd().profit())));
        }
        sb.append("수익률: %s, 오늘: %s".formatted(percent(p.profitRate()), percent(p.dailyProfitRate())));
        for (Holding h : p.holdings()) {
            sb.append("\n- %s %s %s주 | 평가 %s %s | 손익 %s %s (%s)"
                    .formatted(
                            h.symbol(),
                            h.name(),
                            plain(h.quantity()),
                            plain(h.value()),
                            h.currency(),
                            signed(h.profit()),
                            h.currency(),
                            percent(h.profitRate())));
            if (h.weight() != null) {
                sb.append(" | 비중 %s".formatted(share(h.weight())));
            }
        }
        return sb.toString();
    }

    /** 소수 비율(0.1234) → "+12.34%". */
    static String percent(BigDecimal rate) {
        if (rate == null) {
            return "-";
        }
        BigDecimal pct = rate.movePointRight(2).setScale(2, RoundingMode.HALF_UP);
        return (pct.signum() > 0 ? "+" : "") + pct.toPlainString() + "%";
    }

    /** 비중(0.8080) → "80.80%" (부호 없음). */
    static String share(BigDecimal ratio) {
        return ratio.movePointRight(2).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String signed(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return (value.signum() > 0 ? "+" : "") + value.toPlainString();
    }

    private static String dash(String value) {
        return value == null ? "-" : value;
    }

    @McpTool(
            name = "get_buying_power",
            description = "주문 전에 매수 가능 현금을 확인할 때 사용합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getBuyingPower(
            @McpToolParam(description = "통화: KRW(국내) 또는 USD(미국). 기본 KRW", required = false) String currency) {
        return ToolErrors.handle(() -> {
            BuyingPower bp = account.buyingPower(currency);
            return "매수 가능 금액: %s %s".formatted(bp.cash().toPlainString(), bp.currency());
        });
    }

    @McpTool(
            name = "list_orders",
            description = "미체결(OPEN) 또는 완료(CLOSED) 주문 목록을 확인할 때 사용합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listOrders(
            @McpToolParam(description = "OPEN(미체결) 또는 CLOSED(체결·취소 완료). 기본 OPEN", required = false) String status) {
        return ToolErrors.handle(() -> {
            List<OrderItem> orders = account.orders(status);
            if (orders.isEmpty()) {
                return "주문이 없습니다.";
            }
            return orders.stream()
                    .map(o -> "%s %s %s %s 수량 %s 가격 %s %s (%s)"
                            .formatted(
                                    o.orderId(),
                                    o.symbol(),
                                    o.side(),
                                    o.orderType(),
                                    plain(o.quantity()),
                                    plain(o.price()),
                                    o.currency(),
                                    o.status()))
                    .collect(Collectors.joining("\n"));
        });
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.toPlainString();
    }
}
