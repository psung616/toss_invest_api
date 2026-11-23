package io.github.psung616.tossmcp.cli;

/**
 * 사용법 출력. stdout 사용은 cli 패키지에서만 허용된다.
 */
public final class Usage {

    private Usage() {}

    public static void print() {
        System.out.println("""
                tossinvest-mcp — 토스증권 Open API 로컬 MCP 서버 (비공식)

                사용법: java -jar tossinvest-mcp-<버전>.jar <모드>

                모드:
                  serve        MCP 서버(stdio)로 실행. Claude Desktop/Code 에 등록해서 사용
                  setup        API 키를 OS 자격 증명 저장소에 저장
                  reset-keys   저장된 키와 토큰 캐시 삭제
                  diagnose     민감정보 없는 진단 정보 출력
                """);
    }

    public static int notImplemented(String mode) {
        System.err.println(mode + ": not implemented");
        return 2;
    }
}
