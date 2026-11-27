package io.github.psung616.tossmcp.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 웹 UI 정적 파일 규칙 (WEB_SECURITY WS-UI-1~5): 외부 리소스 없음, HTML 삽입·eval·인라인 스크립트 없음. */
class StaticUiTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    private static List<Path> files() throws IOException {
        try (Stream<Path> walk = Files.walk(STATIC)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    void uiFilesExist() throws IOException {
        assertThat(files()).extracting(p -> p.getFileName().toString()).contains("index.html", "app.js", "app.css");
    }

    @Test
    void noExternalUrls() throws IOException {
        for (Path p : files()) {
            assertThat(read(p))
                    .as(p.toString())
                    .doesNotContain("http://")
                    .doesNotContain("https://")
                    .doesNotContain("//cdn");
        }
    }

    @Test
    void noHtmlInjectionOrDynamicCode() throws IOException {
        for (Path p : files()) {
            String text = read(p);
            for (String banned : List.of(
                    "innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function")) {
                assertThat(text).as("%s 에 %s", p, banned).doesNotContain(banned);
            }
            assertThat(text).as(p.toString()).doesNotContain("localStorage").doesNotContain("sessionStorage");
        }
    }

    /** 절대 원칙 5 의 화면 쪽: 금액 문자열을 부동소수점으로 바꾸지 않는다(P9-5). */
    @Test
    void moneyIsNeverConvertedToFloatingPoint() throws IOException {
        String js = read(STATIC.resolve("app.js"));
        for (String banned : List.of("parseFloat", "Number(", "toFixed", "toLocaleString", "Math.round")) {
            assertThat(js).as("app.js 에 %s", banned).doesNotContain(banned);
        }
    }

    /** POST 는 postJson 한 곳에서만, 키 저장(RC-003)·주문 미리보기·확정(P9-8) 세 경로만. 조회는 GET. */
    @Test
    void postsOnlyThroughOneHelperToAllowedPaths() throws IOException {
        String js = read(STATIC.resolve("app.js"));
        assertThat(js.split("method: \"POST\"", -1))
                .as("fetch POST 는 postJson 한 곳")
                .hasSize(2);
        assertThat(js).doesNotContain("\"PUT\"").doesNotContain("\"DELETE\"").doesNotContain("\"PATCH\"");
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("postJson\\(\"([^\"]+)\"").matcher(js);
        java.util.Set<String> paths = new java.util.TreeSet<>();
        while (m.find()) {
            paths.add(m.group(1));
        }
        assertThat(paths).containsExactly("/api/keys", "/api/orders/place", "/api/orders/preview");
    }

    /** WS-ORD-3: 확정은 사용자 클릭(order-confirm) 에서만 — place 호출은 confirmOrder 안 한 곳. */
    @Test
    void orderPlaceOnlyFromConfirmButton() throws IOException {
        String js = read(STATIC.resolve("app.js"));
        assertThat(js.split("/api/orders/place", -1)).as("place 호출은 한 곳").hasSize(2);
        int place = js.indexOf("/api/orders/place");
        int confirmFn = js.lastIndexOf("async function confirmOrder", place);
        int previousFn = js.lastIndexOf("async function ", place);
        assertThat(confirmFn).as("place 는 confirmOrder 안에서만").isPositive().isEqualTo(previousFn);
        assertThat(js).contains("getElementById(\"order-confirm\").addEventListener(\"click\", confirmOrder)");
        assertThat(read(STATIC.resolve("index.html"))).contains("id=\"order-card\" hidden");
    }

    @Test
    void noInlineScriptsStylesOrHandlers() throws IOException {
        for (Path p : files()) {
            if (!p.toString().endsWith(".html")) {
                continue;
            }
            String html = read(p);
            assertThat(html).as("인라인 스크립트").doesNotContainPattern("<script(?![^>]*\\bsrc=)[^>]*>");
            assertThat(html).as("인라인 스타일").doesNotContain("<style").doesNotContainPattern("\\sstyle=");
            assertThat(html).as("이벤트 속성").doesNotContainPattern("\\son[a-z]+=");
        }
    }
}
