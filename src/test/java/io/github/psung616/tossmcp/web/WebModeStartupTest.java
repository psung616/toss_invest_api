package io.github.psung616.tossmcp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.psung616.tossmcp.TossMcpApplication;
import io.github.psung616.tossmcp.cli.WebConsole;
import io.modelcontextprotocol.server.McpSyncServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * web 모드를 실제 Tomcat 으로 띄워 확인한다 (RC-001·RC-002, WEB_SECURITY §6): 127.0.0.1 에만 리슨, MCP 꺼짐, 콘솔 접속 URL → 로그인 → 화면·API,
 * 로그인 전에는 모든 경로 401, 에러 응답에 서버 정보 없음. 토스 API 는 부르지 않는다. 포트 0 = 임의 포트.
 */
class WebModeStartupTest {

    private static final Pattern LOGIN_URL =
            Pattern.compile("http://127\\.0\\.0\\.1:\\d+/login\\?code=[A-Za-z0-9_-]{43}");

    private static Path home;
    private static ConfigurableApplicationContext context;
    private static int port;
    private static final HttpClient http = HttpClient.newHttpClient(); // 리다이렉트를 따라가지 않는다(기본값 NEVER)

    @BeforeAll
    static void start() throws Exception {
        home = Files.createTempDirectory("tossmcp-web");
        System.setProperty("tossinvest.home", home.toString());
        context = new SpringApplication(TossMcpApplication.class)
                .run(
                        "--spring.main.web-application-type=servlet",
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--spring.ai.mcp.server.enabled=false",
                        "--spring.ai.mcp.server.stdio=false",
                        "--logging.level.root=WARN");
        port = ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
        System.clearProperty("tossinvest.home");
    }

    private static HttpResponse<String> getPath(String path, String cookie) throws Exception {
        return get("http://127.0.0.1:" + port + path, cookie);
    }

    private static HttpResponse<String> get(String url, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 콘솔 안내를 받아 접속 URL 을 꺼낸다. Enter 입력 없음(빈 stdin). */
    private static String announceAndGetUrl(List<String> opened) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WebConsole.start(
                context,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new ByteArrayInputStream(new byte[0]),
                url -> {
                    opened.add(url);
                    return true;
                });
        String console = out.toString(StandardCharsets.UTF_8);
        Matcher m = LOGIN_URL.matcher(console);
        assertThat(m.find()).as("콘솔에 접속 URL: %s", console).isTrue();
        assertThat(console).contains("Enter").contains("Ctrl+C");
        return m.group();
    }

    @Test
    void listensOnLoopback() throws IOException {
        assertThat(port).isPositive();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
            assertThat(socket.isConnected()).isTrue();
        }
    }

    @Test
    void doesNotListenOnNonLoopbackAddresses() throws IOException {
        List<InetAddress> lanAddresses = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .filter(WebModeStartupTest::isUp)
                .flatMap(NetworkInterface::inetAddresses)
                .filter(a -> a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress())
                .toList();
        assumeFalse(lanAddresses.isEmpty(), "이 PC 에 비루프백 IPv4 주소가 없어 검사할 수 없음");

        for (InetAddress address : lanAddresses) {
            assertThatThrownBy(() -> {
                        try (Socket socket = new Socket()) {
                            socket.connect(new InetSocketAddress(address, port), 2000);
                        }
                    })
                    .as("%s:%d 로 연결되면 안 된다", address.getHostAddress(), port)
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void mcpServerIsDisabled() {
        assertThat(context.getBeanNamesForType(McpSyncServer.class)).isEmpty();
    }

    @Test
    void everythingRequiresLoginFirst() throws Exception {
        for (String path : List.of(
                "/",
                "/index.html",
                "/app.js",
                "/app.css",
                "/api/status",
                "/api/quote?symbol=005930",
                "/api/stocks/search?query=a",
                "/api/buying-power",
                "/api/orders",
                "/mcp",
                "/actuator")) {
            HttpResponse<String> response = getPath(path, null);
            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(response.body()).as(path).isEmpty();
        }
    }

    @Test
    void consoleUrlLogsInAndShowsHomeAndStatus() throws Exception {
        List<String> opened = new ArrayList<>();
        String url = announceAndGetUrl(opened);
        assertThat(opened).as("브라우저 자동 열기(기본 켬)").containsExactly(url);

        HttpResponse<String> login = get(url, null);
        assertThat(login.statusCode()).isEqualTo(303);
        assertThat(login.headers().firstValue("Location")).hasValue("/");
        List<String> setCookies = login.headers().allValues("Set-Cookie");
        assertThat(setCookies).noneMatch(c -> c.startsWith("JSESSIONID"));
        String cookie = setCookies.stream()
                .filter(c -> c.startsWith("tim_session="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];

        HttpResponse<String> home = getPath("/", cookie);
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("app.js").doesNotContain("<script>");
        assertThat(getPath("/app.js", cookie).statusCode()).isEqualTo(200);

        HttpResponse<String> status = getPath("/api/status", cookie);
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.headers().firstValue("Content-Type"))
                .hasValueSatisfying(t -> assertThat(t).startsWith("application/json"));
        assertThat(status.body()).contains("\"dryRun\":true").contains("\"ordersEnabled\":false");

        assertThat(get(url, null).statusCode()).as("접속 코드 재사용").isEqualTo(401);
    }

    @Test
    void errorResponsesRevealNothing() throws Exception {
        String url = announceAndGetUrl(new ArrayList<>());
        String cookie = get(url, null).headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("tim_session="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];

        HttpResponse<String> notFound = getPath("/does-not-exist", cookie);
        assertThat(notFound.statusCode()).isEqualTo(404);
        assertThat(notFound.body()).isEmpty();
        assertThat(notFound.headers().firstValue("Server")).isEmpty();
        assertThat(notFound.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(notFound.headers().firstValue("Content-Security-Policy")).isPresent();
    }

    private static boolean isUp(NetworkInterface nic) {
        try {
            return nic.isUp();
        } catch (IOException e) {
            return false;
        }
    }
}
