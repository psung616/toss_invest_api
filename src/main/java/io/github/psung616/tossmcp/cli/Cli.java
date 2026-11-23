package io.github.psung616.tossmcp.cli;

import java.awt.GraphicsEnvironment;
import java.io.Console;

/**
 * serve 이외의 모드 진입점. stdout 사용은 cli 패키지에서만 허용된다.
 */
public final class Cli {

    private Cli() {}

    /**
     * 인자 없이 실행됐을 때 설정 창을 연다:
     * <ul>
     *   <li>jar 더블클릭 (javaw, 콘솔 없음)</li>
     *   <li>설치형 실행 파일(tossinvest-mcp.exe) 더블클릭 — 콘솔 프로그램이라 콘솔이 있어도 창을 띄운다</li>
     * </ul>
     * 터미널에서 java -jar 로 실행했다면 사용법을 출력한다.
     */
    public static void noArgs() {
        boolean gui = !GraphicsEnvironment.isHeadless();
        if (gui && (System.console() == null || isPackagedLauncher())) {
            SetupWindow.open();
            return;
        }
        Usage.print();
    }

    /** java/javaw 가 아니라 jpackage 로 만든 실행 파일로 실행 중인지. */
    static boolean isPackagedLauncher() {
        return ProcessHandle.current()
                .info()
                .command()
                .map(c -> !java.nio.file.Path.of(c)
                        .getFileName()
                        .toString()
                        .toLowerCase(java.util.Locale.ROOT)
                        .startsWith("java"))
                .orElse(false);
    }

    public static int setup(boolean forceGui) {
        Console console = System.console();
        if (!forceGui && console != null) {
            return new ConsoleSetup(console, new SetupSteps()).run();
        }
        if (!GraphicsEnvironment.isHeadless()) {
            SetupWindow.open();
            return 0;
        }
        System.err.println("터미널(대화형 콘솔)에서 실행하세요: java -jar tossinvest-mcp-<버전>.jar setup");
        return 1;
    }

    public static int resetKeys() {
        SetupSteps.Outcome outcome = new SetupSteps().resetKeys();
        System.out.println((outcome.ok() ? "[OK] " : "[!]  ") + outcome.message());
        return outcome.ok() ? 0 : 1;
    }
}
