package io.github.psung616.tossmcp.logging;

import java.util.regex.Pattern;

/**
 * 로그 문자열에서 키·토큰·계좌 식별자·수량·금액을 {@code ***} 로 바꾼다.
 *
 * <p>애초에 이런 값을 로그에 남기지 않는 것이 원칙이고, 이 클래스는 실수에 대비한 마지막 방어층이다. 오탐(필요 이상으로 가리는 것)은 허용하고 미탐을 줄이는 쪽으로
 * 패턴을 넓게 잡는다.
 */
public final class Masking {

    static final String MASK = "***";

    /** 이름에 이 단어가 들어간 키는 값이 민감하다고 본다 (대소문자 무시, 예: lastPrice, orderAmount, cashBuyingPower). */
    private static final String SENSITIVE_KEY =
            "[A-Za-z_]*(?i:token|secret|client_?id|account_?seq|account_?no|quantity|amount|price|balance|buying_?power)"
                    + "[A-Za-z_]*";

    /** JSON: "key": "값" | 숫자 | true/false/null */
    private static final Pattern JSON_FIELD = Pattern.compile("\"(" + SENSITIVE_KEY + ")\"\\s*:\\s*"
            + "(?:\"(?:[^\"\\\\]|\\\\.)*\"|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|true|false|null)");

    /** form/query 문자열과 record toString: key=값 */
    private static final Pattern KEY_VALUE = Pattern.compile("\\b(" + SENSITIVE_KEY + ")=[^\\s,&\\])}\"]+");

    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9\\-._~+/]+=*");

    private static final Pattern ACCOUNT_HEADER =
            Pattern.compile("(?i)(X-Tossinvest-Account\"?\\s*[:=]\\s*\"?)[^\\s,;\"\\]}]+");

    /** web 모드 세션 쿠키 값 (WEB_SECURITY WS-LOG-2). */
    private static final Pattern SESSION_COOKIE = Pattern.compile("\\b(tim_session)=[^\\s;,&\"]+");

    /** web 모드 접속 코드: 32자 이상 Base64URL 만 (토스 에러 코드처럼 짧은 code=값은 진단용으로 남긴다). */
    private static final Pattern LAUNCH_CODE = Pattern.compile("\\b(code)=[A-Za-z0-9_-]{32,}");

    /** Cookie / Set-Cookie 헤더 값 전체. */
    private static final Pattern COOKIE_HEADER = Pattern.compile("(?i)\\b((?:Set-)?Cookie)(\\s*[:=]\\s*)[^\\r\\n]+");

    /** 토스증권 Client ID 접두어 */
    private static final Pattern CLIENT_ID_PREFIX = Pattern.compile("\\btsck_[A-Za-z0-9_\\-]+");

    private Masking() {}

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String s = JSON_FIELD.matcher(text).replaceAll("\"$1\":\"" + MASK + "\"");
        s = KEY_VALUE.matcher(s).replaceAll("$1=" + MASK);
        s = BEARER.matcher(s).replaceAll("Bearer " + MASK);
        s = ACCOUNT_HEADER.matcher(s).replaceAll("$1" + MASK);
        s = COOKIE_HEADER.matcher(s).replaceAll("$1$2" + MASK);
        s = SESSION_COOKIE.matcher(s).replaceAll("$1=" + MASK);
        s = LAUNCH_CODE.matcher(s).replaceAll("$1=" + MASK);
        return CLIENT_ID_PREFIX.matcher(s).replaceAll("tsck_" + MASK);
    }
}
