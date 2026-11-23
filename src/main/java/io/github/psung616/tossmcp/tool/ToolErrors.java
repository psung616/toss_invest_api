package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.order.GuardrailViolationException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 도구 경계에서 예외를 사용자 안내 문장으로 바꾼다. 스택트레이스는 노출하지 않는다.
 */
final class ToolErrors {

    private static final Logger log = LoggerFactory.getLogger(ToolErrors.class);

    private ToolErrors() {}

    static String handle(Supplier<String> action) {
        try {
            return action.get();
        } catch (GuardrailViolationException e) {
            return "[주문 거부] " + e.getMessage();
        } catch (TossApiException e) {
            log.warn("Toss API error status={} code={} requestId={}", e.status(), e.code(), e.requestId());
            return "[토스증권 API 오류] " + e.toUserMessage();
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
            return "[요청 오류] " + e.getMessage();
        }
    }
}
