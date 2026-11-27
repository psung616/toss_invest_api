package io.github.psung616.tossmcp.web.api;

/** REST 입력 검증 실패 (WS-REQ-8) → 400. 메시지는 사용자에게 그대로 보여 주므로 입력값을 넣지 않는다. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
