package io.github.psung616.tossmcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.client.auth.TokenIssuer;
import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.secret.CredentialStore;
import io.github.psung616.tossmcp.secret.Credentials;
import io.github.psung616.tossmcp.service.DiagnosticsService.RecentError;
import io.github.psung616.tossmcp.service.DiagnosticsService.Report;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P3-5 diagnose 수집. 키·토큰·로그 내용은 모두 지어낸 값이다. 실제 API 를 부르지 않는다. */
class DiagnosticsServiceTest {

    @TempDir
    Path dir;

    private static final CredentialStore KEYS = () -> Optional.of(new Credentials("fake-client-id", "fake-secret"));
    private static final TokenIssuer OK = c -> new TokenResponse("fake-token-value", "Bearer", 86400);

    @Test
    void offlineSkipsConnection() {
        Report r = DiagnosticsServiceTestSupport.service(dir, KEYS, c -> {
                    throw new AssertionError("offline 인데 토큰 발급 호출");
                })
                .report(false);

        assertThat(r.connection()).isEqualTo("건너뜀 (--offline)");
        assertThat(r.keysConfigured()).isTrue();
        assertThat(r.ordersEnabled()).isFalse();
        assertThat(r.dryRun()).isTrue();
        assertThat(r.version()).isEqualTo("0.1.0");
    }

    @Test
    void connectionSuccessAndTokenCacheFlag() {
        DiagnosticsService service = DiagnosticsServiceTestSupport.service(dir, KEYS, OK);

        assertThat(service.report(true).connection()).isEqualTo("성공");
        assertThat(service.report(false).tokenCacheExists()).isTrue();
    }

    @Test
    void connectionFailureShowsOnlyStatusCodeAndRequestId() {
        Report r = DiagnosticsServiceTestSupport.service(dir, KEYS, c -> {
                    throw new TossApiException(
                            401, "invalid_client", "Client authentication failed: fake-client-id", "req-1");
                })
                .report(true);

        assertThat(r.connection()).isEqualTo("실패 (status=401, code=invalid_client, requestId=req-1)");
    }

    @Test
    void noKeysSkipsConnection() {
        assertThat(DiagnosticsServiceTestSupport.service(dir, Optional::empty, OK)
                        .report(true)
                        .connection())
                .isEqualTo("건너뜀 (키 없음)");
    }

    @Test
    void recentErrorsAreLastFiveCodesOnly() throws IOException {
        Path logs = dir.resolve("logs");
        Files.createDirectories(logs);
        String prefix = "2026-11-27T10:00:0%d.000+09:00  WARN [main] i.g.p.tossmcp.tool.ToolErrors : ";
        Files.writeString(
                logs.resolve("server.1.log"),
                prefix.formatted(1) + "Toss API error status=403 code=ip-not-allowed requestId=r-old1\n"
                        + prefix.formatted(2)
                        + "Toss API error status=429 code=rate-limit-exceeded requestId=r-old2\n");
        Files.writeString(
                logs.resolve("server.log"),
                prefix.formatted(3) + "Toss API error status=401 code=invalid_client requestId=r-3\n"
                        + prefix.formatted(4) + "Bearer fake-token-value accountSeq=123456 lastPrice=71000\n"
                        + prefix.formatted(4) + "Toss API error status=500 code=maintenance requestId=r-4\n"
                        + prefix.formatted(5) + "Toss API error status=404 code=stock-not-found requestId=r-5\n"
                        + prefix.formatted(6) + "Toss API error status=400 code=bad requestId=<script>\n"
                        + prefix.formatted(7) + "Toss API error status=403 code=ip-not-allowed requestId=r-7\n");

        List<RecentError> errors = DiagnosticsService.recentErrors(logs);

        assertThat(errors).extracting(RecentError::requestId).containsExactly("r-old2", "r-3", "r-4", "r-5", "r-7");
        assertThat(errors.getFirst())
                .isEqualTo(new RecentError("2026-11-27T10:00:02", "429", "rate-limit-exceeded", "r-old2"));
    }

    @Test
    void auditEventCount() throws IOException {
        Path logs = dir.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("audit.jsonl"), "{\"a\":1}\n{\"a\":2}\n\n{\"a\":3}\n");

        assertThat(DiagnosticsService.countLines(logs.resolve("audit.jsonl"))).isEqualTo(3);
        assertThat(DiagnosticsService.countLines(logs.resolve("missing.jsonl"))).isZero();
    }

    @Test
    void userHomeIsHiddenInPaths() {
        Path inHome = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "tossinvest-mcp", "KILL");

        assertThat(DiagnosticsService.hideUserHome(inHome))
                .startsWith("~")
                .doesNotContain(System.getProperty("user.home"));
    }

    @Test
    void portStateDetectsListeningPort() throws IOException {
        int port;
        try (ServerSocket busy = new ServerSocket()) {
            busy.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            port = busy.getLocalPort();
            assertThat(DiagnosticsService.portState(port)).startsWith("사용 중");
        }
        assertThat(DiagnosticsService.portState(port)).isEqualTo("비어 있음");
    }
}
