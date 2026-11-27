package io.github.psung616.tossmcp.cli;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 기본 브라우저로 접속 URL 열기 (WEB_SECURITY WS-AUTH-8). Spring Boot 가 headless 로 실행되므로 AWT {@code Desktop} 대신 OS 명령을 쓴다. 셸을 거치지
 * 않고 인자로만 넘긴다(URL 은 {@code http://127.0.0.1:<포트>/login?code=<Base64URL>} 이라 특수문자 없음). 실패해도 서버는 계속 돈다.
 */
@FunctionalInterface
public interface BrowserOpener {

    /** @return 열기 명령을 시작했으면 true */
    boolean open(String url);

    static BrowserOpener system() {
        return url -> {
            if (!url.startsWith("http://127.0.0.1:")) {
                return false;
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            List<String> command;
            if (os.contains("win")) {
                command = List.of("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                command = List.of("open", url);
            } else {
                command = List.of("xdg-open", url);
            }
            try {
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                return true;
            } catch (IOException e) {
                return false;
            }
        };
    }
}
