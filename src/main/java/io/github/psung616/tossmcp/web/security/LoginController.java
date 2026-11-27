package io.github.psung616.tossmcp.web.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /login?code=…} (WEB_SECURITY WS-AUTH-7, WS-SES-1). 성공하면 세션 쿠키를 주고 303 으로 {@code /} 로 보내 주소창에 코드가 남지 않게
 * 한다. 실패는 본문 없는 401.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LoginController {

    private final LaunchCodeStore codes;
    private final SessionStore sessions;

    public LoginController(LaunchCodeStore codes, SessionStore sessions) {
        this.codes = codes;
        this.sessions = sessions;
    }

    @GetMapping(LocalAccessFilter.LOGIN_PATH)
    public ResponseEntity<Void> login(@RequestParam(name = "code", required = false) String code) {
        if (codes.redeem(code) != LaunchCodeStore.Result.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 만료 지정 없음 = 브라우저를 닫으면 지워지는 세션 쿠키. HTTP 루프백이라 Secure 는 붙이지 않는다(WS-NET-6).
        ResponseCookie cookie = ResponseCookie.from(SessionStore.COOKIE, sessions.create())
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .build();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/")
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
