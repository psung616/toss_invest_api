package io.github.psung616.tossmcp.client.error;

/**
 * 토스증권 API 실패. 실패 봉투 {@code {"error": {requestId, code, message, data}}} 를 담는다.
 *
 * TODO(P1-6): 상태별 하위 클래스, 봉투가 아닌 응답(HTML·빈 바디) 처리 테스트
 */
public class TossApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final String requestId;
    private final Integer retryAfterSeconds;

    public TossApiException(int status, String code, String message, String requestId) {
        this(status, code, message, requestId, null);
    }

    public TossApiException(int status, String code, String message, String requestId, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.requestId = requestId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String requestId() {
        return requestId;
    }

    /** 429 응답의 Retry-After(초). 없으면 null. */
    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** 요청 한도 초과(429)인지. */
    public boolean isRateLimited() {
        return status == 429;
    }

    /** 토큰을 재발급하면 해결되는 401 인지. */
    public boolean isTokenExpired() {
        return status == 401 && ("expired-token".equals(code) || "token-revoked".equals(code));
    }

    /** 사용자에게 보여줄 한국어 안내 + requestId. 스택트레이스는 포함하지 않는다. */
    public String toUserMessage() {
        String guide = UserMessages.of(code);
        return requestId == null ? guide : guide + " (requestId: " + requestId + ")";
    }
}
