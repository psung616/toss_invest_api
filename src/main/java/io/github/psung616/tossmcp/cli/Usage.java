package io.github.psung616.tossmcp.cli;

/**
 * 사용법 출력. stdout 사용은 cli 패키지에서만 허용된다.
 */
public final class Usage {

    private Usage() {}

    public static void print() {
        System.out.println("""
                tossinvest-mcp - 토스증권 Open API 로컬 MCP 서버 (비공식)

                처음이라면: setup 을 실행하세요 (또는 jar 파일을 더블클릭).
                  키 입력 -> 연결 테스트 -> Claude Code 자동 등록까지 한 번에 진행합니다.

                사용법: java -jar tossinvest-mcp-<버전>.jar <모드>

                모드:
                  setup        키 입력 마법사 (setup --gui : 창으로 열기)
                  serve        MCP 서버(stdio)로 실행. Claude 가 자동으로 실행하므로 직접 쓸 일은 없음
                  web          내 PC 브라우저용 화면 (127.0.0.1 전용). setup 을 먼저 실행하세요
                  reset-keys   저장된 키 삭제
                  diagnose     민감정보 없는 진단 정보 출력 (준비 중)
                """);
    }

    public static int notImplemented(String mode) {
        System.err.println(mode + ": not implemented");
        return 2;
    }
}
