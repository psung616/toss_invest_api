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
