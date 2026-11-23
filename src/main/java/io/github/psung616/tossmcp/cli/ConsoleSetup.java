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

        if (enterKeys) {
            String clientId = console.readLine("Client ID: ");
            char[] secret = console.readPassword("Client Secret (입력해도 화면에 표시되지 않습니다): ");
            try {
                SetupSteps.Outcome saveResult = steps.save(clientId, secret);
                print(saveResult);
                if (!saveResult.ok()) {
                    return 1;
                }
            } finally {
                if (secret != null) {
                    Arrays.fill(secret, '\0');
                }
            }
        }

        out.println("연결 테스트 중...");
        out.flush();
        print(steps.testConnection());

        Optional<Path> claude = steps.findClaudeCode();
        if (claude.isPresent()) {
            if (yes("Claude Code 에 등록할까요?", true)) {
                out.println("등록 중... (10초 정도 걸릴 수 있습니다)");
                out.flush();
                SetupSteps.Outcome r = steps.registerClaudeCode(claude.get());
                print(r);
                if (r.ok()) {
                    out.println("      새 Claude Code 세션을 열고 \"삼성전자 현재가 알려줘\" 라고 물어보세요.");
                }
            }
        } else {
            out.println("[안내] Claude Code 가 설치되어 있지 않아 자동 등록을 건너뜁니다.");
            out.println("       Claude Desktop 사용자는 tossinvest-mcp .mcpb 파일을 더블클릭해 설치하세요.");
        }
        out.println();
        out.println("완료. 키를 지우려면: tossinvest-mcp reset-keys");
        out.flush();
        return 0;
    }

    private boolean yes(String question, boolean defaultYes) {
        String answer = console.readLine("%s %s ", question, defaultYes ? "[Y/n]" : "[y/N]");
        if (answer == null || answer.isBlank()) {
            return defaultYes;
        }
        return answer.strip().toLowerCase(Locale.ROOT).startsWith("y");
    }

    private void print(SetupSteps.Outcome outcome) {
        out.println((outcome.ok() ? "[OK] " : "[!]  ") + outcome.message());
        out.flush();
    }
}
