package io.github.psung616.tossmcp.cli;

import io.github.psung616.tossmcp.secret.CompositeCredentialStore;
import io.github.psung616.tossmcp.secret.CredentialStore;
import io.github.psung616.tossmcp.secret.EnvCredentialStore;
import io.github.psung616.tossmcp.secret.KeyringCredentialStore;
import io.github.psung616.tossmcp.secret.OsKeyring;
import io.github.psung616.tossmcp.web.security.LoopbackViolationException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * {@code web} 모드 진입 (RC-001, ARCHITECTURE §12.1). 키 확인(없으면 화면 입력 안내 — RC-003) → web 전용 설정 고정 → 스프링 기동 → 콘솔 접속 안내({@link WebConsole}). 실패 이유는
 * 한국어로 stderr 에 안내한다.
 *
 * <p>설정은 시스템 프로퍼티로 덮어써서 사용자가 {@code -Dserver.address=0.0.0.0} 같은 JVM 옵션을 줘도 127.0.0.1 로 고정된다. 그래도 바뀐 경우를
 * 대비해 {@code LoopbackGuard} 가 기동 시 다시 검사한다.
 */
public final class WebLauncher {

    static final Map<String, String> WEB_PROPERTIES = Map.of(
            "spring.main.web-application-type", "servlet",
            "server.address", "127.0.0.1",
            // web 모드에서는 MCP 서버를 켜지 않는다 (stdio 를 쓰지 않고, HTTP 로 MCP 를 노출하지도 않는다).
            "spring.ai.mcp.server.enabled", "false",
            "spring.ai.mcp.server.stdio", "false");

    private WebLauncher() {}

    /** @param starter 스프링 컨텍스트를 띄우는 동작. 성공하면 Tomcat 스레드가 프로세스를 유지한다. */
    public static int run(Supplier<ConfigurableApplicationContext> starter) {
        return run(
                defaultCredentialStore(),
                starter,
                System.err,
                context -> WebConsole.start(context, System.out, System.in, BrowserOpener.system()));
    }

    static int run(
            CredentialStore credentials,
            Supplier<ConfigurableApplicationContext> starter,
            PrintStream err,
            Consumer<ConfigurableApplicationContext> onStarted) {
        if (credentials.load().isEmpty()) {
            // RC-003: 키가 없어도 기동한다. 로그인한 화면에서 키를 입력하면 된다.
            err.println("[i]  저장된 키가 없습니다. 열리는 브라우저 화면에서 키를 입력하세요.");
        }
        WEB_PROPERTIES.forEach(System::setProperty);
        ConfigurableApplicationContext context;
        try {
            context = starter.get();
        } catch (RuntimeException e) {
            err.println(failureMessage(e));
            return 1;
        }
        onStarted.accept(context);
        return 0;
    }

    static String failureMessage(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof PortInUseException portInUse) {
                return "[!]  포트 " + portInUse.getPort() + " 를 이미 다른 프로그램이 쓰고 있습니다. "
                        + "그 프로그램을 끄거나 환경변수 TOSSINVEST_WEB_PORT 로 다른 포트(1024~65535)를 지정하세요.";
            }
            if (t instanceof LoopbackViolationException violation) {
                return "[!]  " + violation.getMessage();
            }
        }
        // 예외 메시지에는 무엇이 들어 있을지 모르므로 콘솔에 그대로 쓰지 않는다. 자세한 내용은 마스킹된 로그 파일에 있다.
        return "[!]  web 모드를 시작하지 못했습니다. 자세한 내용은 로그 폴더의 server.log 를 확인하세요.";
    }

    private static CredentialStore defaultCredentialStore() {
        return new CompositeCredentialStore(
                List.of(new EnvCredentialStore(), new KeyringCredentialStore(OsKeyring.detect())));
    }
}
