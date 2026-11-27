package io.github.psung616.tossmcp.web.api;

import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.service.AppStatusService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * 조회 REST 의 에러 형식 (WS-RES-5): {@code {"error":{"code","message","requestId"}}}. 토스 상태를 그대로 쓰지 않고 정규화한다. 특히 토스의
 * 401·403(키 오류·허용 IP)은 <b>502</b> 로 바꾼다 — 화면은 401 을 "접속(세션) 만료"로 해석하기 때문이다. 스택트레이스·예외 메시지 원문은 응답에 넣지 않는다.
 */
@RestControllerAdvice(assignableTypes = QueryController.class)
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
