package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.config.AppPaths;
import io.github.psung616.tossmcp.config.KillSwitch;
import io.github.psung616.tossmcp.config.TossProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * {@code diagnose} 진단 정보 (P3-5, DECISIONS "diagnose 모드"). 버그 제보 때 사용자가 그대로 보내도 되는 값만 모은다:
 * 버전·OS·Java, 키 <b>설정 여부</b>, 토큰 캐시 존재 여부, 연결 결과(성공/에러 코드), 최근 토스 에러 코드·requestId, 주문 설정 플래그, 감사로그 건수,
 * KILL 파일 경로, web 포트 설정·사용 여부.
 *
 * <p>절대 담지 않는 것: 키·토큰·accountSeq·계좌번호·금액·보유 종목·주문 내용·로그 원문. 경로는 사용자 폴더를 {@code ~} 로 바꿔 사용자 이름을 숨긴다.
 */
@Service
public class DiagnosticsService {

    static final int RECENT_ERRORS = 5;

    /** 로그에서 뽑는 것은 상태·코드·requestId·시각뿐. 각 값은 좁은 문자 집합으로만 받는다. */
    private static final Pattern TOSS_ERROR =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})\\S*\\s.*Toss API error status=(\\d{1,3}) "
                    + "code=([A-Za-z0-9_.\\-]{1,64}) requestId=([A-Za-z0-9_.\\-]{1,100})\\s*$");

    public record RecentError(String time, String status, String code, String requestId) {}

    public record Report(
            String version,
            String os,
            String java,
            boolean keysConfigured,
            boolean keysFromEnvironment,
            boolean tokenCacheExists,
            String connection,
            List<RecentError> recentErrors,
            boolean ordersEnabled,
            boolean dryRun,
            boolean killSwitchActive,
            String killFile,
            long auditEvents,
            int webPort,
            String webPortState) {}

    private final AppStatusService status;
    private final TossProperties properties;
    private final AppPaths paths;
    private final KillSwitch killSwitch;
    private final TokenManager tokens;
    private final int webPort;

    public DiagnosticsService(
            AppStatusService status,
            TossProperties properties,
            AppPaths paths,
            KillSwitch killSwitch,
            TokenManager tokens,
            @Value("${server.port:8765}") int webPort) {
        this.status = status;
        this.properties = properties;
        this.paths = paths;
        this.killSwitch = killSwitch;
        this.tokens = tokens;
        this.webPort = webPort;
    }

    /** @param checkConnection false 면 토스 API 를 부르지 않는다({@code diagnose --offline}) */
    public Report report(boolean checkConnection) {
        AppStatusService.AppStatus s = status.status();
        return new Report(
                s.version(),
                System.getProperty("os.name") + " " + System.getProperty("os.version") + " ("
                        + System.getProperty("os.arch") + ")",
                System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
                s.keysConfigured(),
                s.keysFromEnvironment(),
                Files.exists(paths.cacheDir().resolve("token.json")),
                connection(s.keysConfigured(), checkConnection),
                recentErrors(paths.logsDir()),
                properties.enableOrders(),
                properties.dryRun(),
                killSwitch.isActive(),
                hideUserHome(killSwitch.killFile()),
                countLines(paths.logsDir().resolve("audit.jsonl")),
                webPort,
                portState(webPort));
    }

    private String connection(boolean keysConfigured, boolean checkConnection) {
        if (!checkConnection) {
            return "건너뜀 (--offline)";
        }
        if (!keysConfigured) {
            return "건너뜀 (키 없음)";
        }
        try {
            tokens.getAccessToken(); // 유효한 캐시 토큰이 있으면 재사용 — 다른 프로세스의 토큰을 무효화하지 않는다
            return "성공";
        } catch (TossApiException e) {
            return "실패 (status=" + e.status() + ", code=" + safe(e.code()) + ", requestId=" + safe(e.requestId()) + ")";
        } catch (ResourceAccessException e) {
            return "실패 (토스증권 서버에 연결할 수 없음)";
        } catch (RuntimeException e) {
            return "실패 (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** server.log(최신)와 회전된 server.1~3.log 에서 최근 토스 에러 코드 5개. */
    static List<RecentError> recentErrors(Path logsDir) {
        List<Path> files = new ArrayList<>();
        for (int i = 3; i >= 1; i--) {
            files.add(logsDir.resolve("server." + i + ".log"));
        }
        files.add(logsDir.resolve("server.log"));
        Deque<RecentError> last = new ArrayDeque<>();
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                lines.forEach(line -> {
                    Matcher m = TOSS_ERROR.matcher(line);
                    if (m.matches()) {
                        last.addLast(new RecentError(m.group(1), m.group(2), m.group(3), m.group(4)));
                        if (last.size() > RECENT_ERRORS) {
                            last.removeFirst();
                        }
                    }
                });
            } catch (IOException | UncheckedIOException e) {
                // 읽을 수 없는 로그는 건너뛴다 (진단은 계속)
            }
        }
        return List.copyOf(last);
    }

    static long countLines(Path file) {
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.filter(l -> !l.isBlank()).count();
        } catch (IOException | UncheckedIOException e) {
            return -1;
        }
    }

    /** 127.0.0.1 에 잠깐 바인딩해 본다(루프백이라 방화벽 창이 뜨지 않음). */
    static String portState(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return "비어 있음";
        } catch (BindException e) {
            return "사용 중 (web 이 실행 중이거나 다른 프로그램이 사용)";
        } catch (IOException | IllegalArgumentException e) {
            return "확인 불가";
        }
    }

    /** 사용자 폴더 경로를 ~ 로 바꿔 사용자 이름이 진단 출력에 나오지 않게 한다. */
    static String hideUserHome(Path path) {
        String text = path.toAbsolutePath().toString();
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank() && text.startsWith(home)) {
            return "~" + text.substring(home.length());
        }
        return text;
    }

    private static String safe(String value) {
        return value != null && value.matches("[A-Za-z0-9_.\\-]{1,100}") ? value : "-";
    }
}
