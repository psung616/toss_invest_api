package io.github.psung616.tossmcp.web.security;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 에러 응답은 본문 없이 상태 코드만 (WEB_SECURITY WS-RES-4). Spring 기본 {@code BasicErrorController} 와 그 HTML 대체 경로(Tomcat 기본 에러 페이지,
 * 서버 버전 노출)를 대신한다 — {@code ErrorController} 빈이 있으면 기본 컨트롤러는 만들어지지 않는다.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SilentErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<Void> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return ResponseEntity.status(status instanceof Integer code ? code : 500)
                .build();
    }
}
