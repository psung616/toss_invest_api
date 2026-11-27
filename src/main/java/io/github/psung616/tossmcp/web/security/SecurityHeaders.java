package io.github.psung616.tossmcp.web.security;

import jakarta.servlet.http.HttpServletResponse;

/** 모든 응답에 붙이는 보안 헤더 (WEB_SECURITY WS-RES-1). CORS 헤더는 어떤 경우에도 붙이지 않는다(WS-RES-2). */
final class SecurityHeaders {

    static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
            + "connect-src 'self'; font-src 'none'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; "
            + "form-action 'self'";

    private SecurityHeaders() {}

    static void apply(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
    }
}
