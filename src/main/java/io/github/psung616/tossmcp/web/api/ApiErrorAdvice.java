package io.github.psung616.tossmcp.web.api;

import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.order.DailyCounterStore;
import io.github.psung616.tossmcp.order.GuardrailViolationException;
import io.github.psung616.tossmcp.service.AppStatusService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * 조회·주문 REST 의 에러 형식 (WS-RES-5): {@code {"error":{"code","message","requestId"}}}. 토스 상태를 그대로 쓰지 않고 정규화한다. 특히 토스의
 * 401·403(키 오류·허용 IP)은 <b>502</b> 로 바꾼다 — 화면은 401 을 "접속(세션) 만료"로 해석하기 때문이다. 스택트레이스·예외 메시지 원문은 응답에 넣지 않는다
 * (우리 코드가 만든 고정 안내 문구 — 입력 검증·가드레일 위반·카운터 파일 — 만 그대로 보여 준다).
 */
@RestControllerAdvice(assignableTypes = {QueryController.class, OrderController.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvice.class);

    private final AppStatusService status;

    public ApiErrorAdvice(AppStatusService status) {
        this.status = status;
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, Object>> invalid(InvalidInputException e) {
        return error(HttpStatus.BAD_REQUEST, "invalid_input", e.getMessage(), null);
    }

    /** OrderCommand 생성자의 형식 검증(고정 문구). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, "invalid_input", e.getMessage(), null);
    }

    /** 깨진 JSON — 파서 메시지에 입력 일부가 들어갈 수 있어 고정 문구로 끝낸다(기본 처리기의 경고 로그로 넘기지 않음). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable() {
        return error(HttpStatus.BAD_REQUEST, "invalid_input", "요청 형식이 올바르지 않습니다.", null);
    }

    /** 가드레일 위반·previewId 없음/만료/재사용 (P9-8). 위반 문구는 GuardrailService 가 만든 고정 안내다. */
    @ExceptionHandler(GuardrailViolationException.class)
    public ResponseEntity<Map<String, Object>> rejected(GuardrailViolationException e) {
        return error(HttpStatus.CONFLICT, "order_rejected", String.join(" / ", e.violations()), null);
    }

    /** 일일 카운터 파일 손상 — 주문 한도를 확인할 수 없어 거부(P4-1, 닫힌 쪽). */
    @ExceptionHandler(DailyCounterStore.CounterFileException.class)
    public ResponseEntity<Map<String, Object>> counterFile(DailyCounterStore.CounterFileException e) {
        log.warn("Order refused: daily counter file unavailable");
        return error(HttpStatus.SERVICE_UNAVAILABLE, "counter_unavailable", e.getMessage(), null);
    }

    @ExceptionHandler(TossApiException.class)
    public ResponseEntity<Map<String, Object>> toss(TossApiException e) {
        log.warn("Toss API error status={} code={} requestId={}", e.status(), e.code(), e.requestId());
        return error(
                statusFor(e.status()), e.code() == null ? "toss_error" : e.code(), e.toUserMessage(), e.requestId());
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> network(ResourceAccessException e) {
        log.warn("Toss API unreachable: {}", e.getClass().getSimpleName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "toss_unreachable", "토스증권 서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요.", null);
    }

    /** 키가 없으면 TokenManager 가 IllegalStateException 을 던진다. 메시지 원문 대신, 실제로 키가 없을 때만 web 용 안내를 쓴다. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> other(RuntimeException e) {
        log.warn("Query failed: {}", e.getClass().getSimpleName());
        if (e instanceof IllegalStateException && !status.status().keysConfigured()) {
            return error(HttpStatus.CONFLICT, "keys_missing", "API 키가 없습니다. 화면의 API 키 칸에서 키를 먼저 저장하세요.", null);
        }
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "조회 중 오류가 났습니다. 로그 폴더의 server.log 를 확인하세요.", null);
    }

    static HttpStatus statusFor(int tossStatus) {
        if (tossStatus == 429) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (tossStatus == 404) {
            return HttpStatus.NOT_FOUND;
        }
        if (tossStatus == 400 || tossStatus == 422) {
            return HttpStatus.BAD_REQUEST;
        }
        if (tossStatus >= 500) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_GATEWAY; // 401·403(키·허용 IP)·0(빈 응답) 등
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", body));
    }
}
