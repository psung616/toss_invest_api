package io.github.psung616.tossmcp.cli;

import io.github.psung616.tossmcp.config.WebProperties;
import io.github.psung616.tossmcp.web.security.LaunchCodeStore;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * web 모드 콘솔 안내 (WEB_SECURITY WS-AUTH-2·6·8, WS-LOG-3·4). 접속 코드는 <b>콘솔(stdout)에만</b> 출력하고 로그에는 쓰지 않는다. 콘솔에서 Enter
 * 를 누르면 새 코드를 발급한다(이전 코드 무효). 웹으로 코드를 재발급하는 경로는 없다.
 */
public final class WebConsole {

    private WebConsole() {}

    public static void start(
            ConfigurableApplicationContext context, PrintStream out, InputStream in, BrowserOpener browser) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        LaunchCodeStore codes = context.getBean(LaunchCodeStore.class);
        boolean openBrowser = context.getBean(WebProperties.class).openBrowser();

        String url = loginUrl(port, codes.issue());
        out.println();
        out.println("[OK] tossinvest web 실행 중 - 이 PC 에서만 접속할 수 있습니다.");
        printUrl(out, url);
        out.println("     새 접속 주소가 필요하면 Enter, 종료는 Ctrl+C");
        if (openBrowser && !browser.open(url)) {
            out.println("[!]  브라우저를 자동으로 열지 못했습니다. 위 주소를 브라우저 주소창에 붙여넣으세요.");
        }

        Thread reader = new Thread(() -> reissueOnEnter(port, codes, out, in), "web-console");
        reader.setDaemon(true);
        reader.start();
    }

    static String loginUrl(int port, String code) {
        return "http://127.0.0.1:" + port + "/login?code=" + code;
    }

    private static void printUrl(PrintStream out, String url) {
        out.println("     접속 주소 (2분 안에, 한 번만 쓸 수 있음):");
        out.println("     " + url);
        out.println("     이 주소는 비밀번호와 같습니다. 공유하거나 화면 공유·스크린샷에 노출하지 마세요.");
    }

    private static void reissueOnEnter(int port, LaunchCodeStore codes, PrintStream out, InputStream in) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()))) {
            while (lines.readLine() != null) {
                out.println("[OK] 새 접속 주소를 만들었습니다. 이전 주소는 더 이상 쓸 수 없습니다.");
                printUrl(out, loginUrl(port, codes.issue()));
            }
        } catch (IOException e) {
            // 콘솔 입력이 없는 환경(서비스 실행 등). 재발급만 못 할 뿐 서버는 계속 돈다.
        }
    }
}
