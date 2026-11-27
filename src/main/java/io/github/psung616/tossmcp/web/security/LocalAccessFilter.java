package io.github.psung616.tossmcp.web.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * web 모드의 모든 요청 검사 (WEB_SECURITY §4.2). 순서대로 검사하고 하나라도 실패하면 본문 없이 상태 코드만 돌려준다.
 *
 * <ol>
 *   <li>원격 주소가 루프백 — 아니면 403
 *   <li>{@code Host} 가 정확히 {@code 127.0.0.1:<포트>} — 아니면 421 (DNS rebinding)
 *   <li>{@code Origin} 이 있으면 {@code http://127.0.0.1:<포트>} 만 ({@code null} 거부) — 아니면 403
 *   <li>{@code Sec-Fetch-Site} 가 있으면 {@code same-origin}·{@code none} 만 — 아니면 403 (CSRF)
 *   <li>메서드는 GET·HEAD, 그리고 {@link #POST_PATHS} 의 POST 만 — 아니면 405
 *   <li>{@code /login} 외 모든 경로(정적 파일 포함)는 유효한 세션 쿠키 — 아니면 401
 *   <li>POST 는 JSON(415)·{@code X-Tossinvest-Local: 1}(403)·바디 길이 명시(411)·4KB 이하(413)
 * </ol>
 *
 * 보안 헤더는 검사 전에 붙이므로 거부 응답·에러 응답에도 붙는다. 포트는 요청이 들어온 실제 로컬 포트를 쓴다.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalAccessFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/login";
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "HEAD");
    /** POST 를 받는 경로. 이 외의 POST 는 405 (RC-003 키 저장, P9-8 주문 — 주문 컨트롤러는 enable-orders=true 일 때만 있어 꺼져 있으면 404). */
    static final Set<String> POST_PATHS = Set.of("/api/keys", "/api/orders/preview", "/api/orders/place");

    static final String LOCAL_HEADER = "X-Tossinvest-Local";
    static final int MAX_POST_BODY = 4 * 1024;
    private static final Set<String> ALLOWED_FETCH_SITES = Set.of("same-origin", "none");

    /** 브라우저가 로그인 없이 페이지를 직접 열었을 때만 보여 주는 고정 안내. 동적 내용 없음. */
    static final String GUIDE_PAGE = """
            <!doctype html>
            <html lang="ko"><head><meta charset="utf-8"><title>tossinvest web</title></head>
            <body><h1>접속 주소로 다시 열어 주세요</h1>
            <p>web 모드를 실행한 콘솔 창에 표시된 접속 주소(<code>/login?code=…</code>)를 여세요.</p>
            <p>주소가 만료됐거나 이미 썼다면 콘솔 창에서 Enter 를 눌러 새 주소를 받으세요.</p></body></html>
            """;

    private final SessionStore sessions;

    public LocalAccessFilter(SessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false; // 에러 응답에도 보안 헤더를 붙인다
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityHeaders.apply(response);
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            chain.doFilter(request, response); // 원래 요청이 이미 아래 검사를 통과했다
            return;
        }

        if (!isLoopback(request.getRemoteAddr())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String self = "127.0.0.1:" + request.getLocalPort();
        if (!self.equals(request.getHeader("Host"))) {
            response.setStatus(421);
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.equals("http://" + self)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !ALLOWED_FETCH_SITES.contains(fetchSite)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        boolean post = "POST".equals(request.getMethod()) && POST_PATHS.contains(request.getRequestURI());
        if (!ALLOWED_METHODS.contains(request.getMethod()) && !post) {
            response.setHeader("Allow", POST_PATHS.contains(request.getRequestURI()) ? "POST" : "GET, HEAD");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        if (!LOGIN_PATH.equals(request.getRequestURI()) && !sessions.isValid(sessionCookie(request))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            if ("navigate".equals(request.getHeader("Sec-Fetch-Mode"))) {
                response.setContentType("text/html;charset=UTF-8");
                response.getOutputStream().write(GUIDE_PAGE.getBytes(StandardCharsets.UTF_8));
            }
            return;
        }
        if (post) {
            int status = checkPost(request);
            if (status != 0) {
                response.setStatus(status);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * WS-REQ-7·8: POST 는 JSON + {@code X-Tossinvest-Local: 1} + 바디 크기 제한. 커스텀 헤더는 다른 출처에서 붙이면 프리플라이트(OPTIONS)가 필요한데
     * 그것은 위에서 막히므로, 폼·이미지 태그로 보내는 CSRF 가 여기서 걸린다.
     *
     * @return 0 이면 통과, 아니면 응답 상태
     */
    private static int checkPost(HttpServletRequest request) {
        if (!isJson(request.getContentType())) {
            return HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
        }
        if (!"1".equals(request.getHeader(LOCAL_HEADER))) {
            return HttpServletResponse.SC_FORBIDDEN;
        }
        long length = request.getContentLengthLong();
        if (length < 0) {
            return HttpServletResponse.SC_LENGTH_REQUIRED;
        }
        if (length > MAX_POST_BODY) {
            return HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
        }
        return 0;
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return type.equals("application/json");
    }

    private static String sessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SessionStore.COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 원격 주소는 서블릿 컨테이너가 준 IP 리터럴이라 DNS 조회가 일어나지 않는다. */
    static boolean isLoopback(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isEmpty() || !remoteAddr.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
