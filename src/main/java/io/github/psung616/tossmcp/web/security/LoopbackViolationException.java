package io.github.psung616.tossmcp.web.security;

/** web 모드가 루프백(127.0.0.1) 이외의 주소나 허용 범위 밖 포트로 기동하려 할 때. 메시지는 사용자에게 그대로 보여 준다. */
public class LoopbackViolationException extends IllegalStateException {

    public LoopbackViolationException(String message) {
        super(message);
    }
}
