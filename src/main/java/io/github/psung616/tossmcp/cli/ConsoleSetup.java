package io.github.psung616.tossmcp.cli;

import java.io.Console;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 터미널용 setup 마법사. 비밀값은 화면에 표시되지 않게 readPassword 로 받는다.
 */
final class ConsoleSetup {

    private final Console console;
    private final PrintWriter out;
    private final SetupSteps steps;

    ConsoleSetup(Console console, SetupSteps steps) {
        this.console = console;
        this.out = console.writer();
        this.steps = steps;
    }

    int run() {
        out.println();
        out.println("=== tossinvest-mcp 설정 ===");
        out.println("토스증권 PC 웹(WTS) > 설정 > Open API 에서 발급한 키를 붙여넣으세요.");
        out.println("키는 이 PC 의 " + SetupSteps.keyringName() + "에만 저장되며, 어디로도 전송되지 않습니다.");
        out.println();

        Optional<String> saved = steps.savedClientIdMasked();
        boolean enterKeys = true;
        if (saved.isPresent()) {
            out.println("이미 저장된 키가 있습니다 (Client ID " + saved.get() + ").");
            enterKeys = yes("새 키로 바꿀까요?", false);
        }

        String clientId = "";
        char[] secret = new char[0];
        if (enterKeys) {
            clientId = console.readLine("Client ID: ");
            secret = console.readPassword("Client Secret (입력해도 화면에 표시되지 않습니다): ");
        }
        SetupFlow.Result result;
        try {
            result = new SetupFlow(steps).run(clientId, secret, this::askRegisterTarget, this::progress);
        } finally {
            if (secret != null) {
                Arrays.fill(secret, '\0');
            }
        }
        out.println();
        out.println((result.ok() ? "[OK] " : "[!]  ") + result.summary());
        if (result.ok()) {
            out.println("키를 지우려면: tossinvest-mcp reset-keys");
        }
        out.flush();
        return result.ok() ? 0 : 1;
    }

    /** 연결 테스트가 성공한 뒤에만 불린다. */
    private Optional<Path> askRegisterTarget() {
        Optional<Path> claude = steps.findClaudeCode();
        if (claude.isEmpty()) {
            out.println("[안내] Claude Code 가 설치되어 있지 않아 자동 등록을 건너뜁니다.");
            out.println("       Claude Desktop 사용자는 tossinvest-mcp .mcpb 파일을 더블클릭해 설치하세요.");
            out.flush();
            return Optional.empty();
        }
        return yes("Claude Code 에 등록할까요?", true) ? claude : Optional.empty();
    }

    /** 콘솔은 이모지 대신 [OK]/[!] 로 표시한다 (Windows 콘솔 글꼴). */
    private void progress(String line) {
        out.println(line.replace("✔ ", "[OK] ").replace("✖ ", "[!]  "));
        out.flush();
    }

    private boolean yes(String question, boolean defaultYes) {
        String answer = console.readLine("%s %s ", question, defaultYes ? "[Y/n]" : "[y/N]");
        if (answer == null || answer.isBlank()) {
            return defaultYes;
        }
        return answer.strip().toLowerCase(Locale.ROOT).startsWith("y");
    }
}
