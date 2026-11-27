package io.github.psung616.tossmcp.web.api;

import io.github.psung616.tossmcp.service.KeySetupService;
import io.github.psung616.tossmcp.service.KeySetupService.Result;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/keys} — 화면에서 키 저장 (RC-003, WEB_SECURITY WS-KEY). 세션·JSON·{@code X-Tossinvest-Local}·바디 크기 검사는
 * {@code LocalAccessFilter} 가 먼저 한다. 응답에는 키 값을 절대 넣지 않는다.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KeysController {

    /** Secret 은 {@code char[]} 로 받아 저장 후 지운다. toString 에 값을 넣지 않는다. */
    public record KeyRequest(String clientId, char[] clientSecret) {

        @Override
        public String toString() {
            return "KeyRequest[****]";
        }
    }

    private final KeySetupService keys;

    public KeysController(KeySetupService keys) {
        this.keys = keys;
    }

    @PostMapping(
            path = "/api/keys",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> save(@RequestBody KeyRequest request) {
        Result result = keys.save(request.clientId(), request.clientSecret());
        if (result.saved()) {
            return ResponseEntity.ok(Map.of(
                    "saved",
                    true,
                    "connected",
                    result.outcome() == KeySetupService.Outcome.CONNECTED,
                    "message",
                    result.message()));
        }
        HttpStatus status = switch (result.outcome()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case ENV_KEYS_IN_USE -> HttpStatus.CONFLICT;
            case TOO_MANY_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return error(status, result.outcome().name().toLowerCase(java.util.Locale.ROOT), result.message());
    }

    /**
     * 깨진 JSON. 파서 메시지에는 입력 일부(예: 따옴표 없는 Secret)가 들어갈 수 있으므로, 기본 처리기(경고 로그에 메시지를 남김)로 넘기지 않고 여기서
     * 고정 문구로 끝낸다(WS-KEY-7).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable() {
        return error(HttpStatus.BAD_REQUEST, "invalid_input", "요청 형식이 올바르지 않습니다.");
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", Map.of("code", code, "message", message)));
    }
}
