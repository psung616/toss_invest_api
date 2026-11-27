package io.github.psung616.tossmcp.cli;

import io.github.psung616.tossmcp.service.DiagnosticsService;
import io.github.psung616.tossmcp.service.DiagnosticsService.RecentError;
import io.github.psung616.tossmcp.service.DiagnosticsService.Report;
import java.util.function.Supplier;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * {@code diagnose} 출력 (P3-5). 키·토큰·accountSeq·금액·보유 정보 없이, 버그 제보에 그대로 붙여 넣어도 되는 값만 출력한다(DECISIONS "diagnose 모드").
 * stdout 사용은 cli 패키지에서만 허용된다.
 */
public final class DiagnoseCommand {

    /** diagnose 전용 설정: 포트·MCP 를 열지 않고, 콘솔 로그는 경고 이상만. */
    static final java.util.Map<String, String> PROPERTIES = java.util.Map.of(
            "spring.main.web-application-type", "none",
            "spring.ai.mcp.server.enabled", "false",
            "spring.ai.mcp.server.stdio", "false",
            "logging.level.root", "WARN");

    private DiagnoseCommand() {}

    public static int run(String[] args, Supplier<ConfigurableApplicationContext> starter) {
        boolean offline = args.length > 1 && "--offline".equals(args[1]);
        PROPERTIES.forEach(System::setProperty);
        try (ConfigurableApplicationContext context = starter.get()) {
            Report report = context.getBean(DiagnosticsService.class).report(!offline);
            System.out.print(format(report));
            return 0;
        } catch (RuntimeException e) {
            System.err.println(
                    "[!]  진단 정보를 만들지 못했습니다 (" + e.getClass().getSimpleName() + "). 로그 폴더의 server.log 를 확인하세요.");
            return 1;
        }
    }

    static String format(Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("tossinvest-mcp diagnose — 키·토큰·계좌·금액은 포함하지 않습니다. 이 내용만 제보에 붙여 넣으세요.\n");
        sb.append("버전: ").append(r.version()).append('\n');
        sb.append("OS: ").append(r.os()).append('\n');
        sb.append("Java: ").append(r.java()).append('\n');
        sb.append("키 설정: ")
                .append(r.keysConfigured() ? (r.keysFromEnvironment() ? "예 (환경변수)" : "예 (자격 증명 저장소)") : "아니오")
                .append('\n');
        sb.append("토큰 캐시: ").append(r.tokenCacheExists() ? "있음" : "없음").append('\n');
        sb.append("연결 테스트: ").append(r.connection()).append('\n');
        sb.append("최근 토스 API 에러 (최대 5개):");
        if (r.recentErrors().isEmpty()) {
            sb.append(" 없음\n");
        } else {
            sb.append('\n');
            for (RecentError e : r.recentErrors()) {
                sb.append("  - ")
                        .append(e.time())
                        .append(" status=")
                        .append(e.status())
                        .append(" code=")
                        .append(e.code())
                        .append(" requestId=")
                        .append(e.requestId())
                        .append('\n');
            }
        }
        sb.append("주문 기능: ").append(r.ordersEnabled() ? "켜짐" : "꺼짐");
        sb.append(", DRY_RUN: ").append(r.dryRun() ? "예" : "아니오");
        sb.append(", 킬 스위치: ").append(r.killSwitchActive() ? "작동 중" : "꺼짐").append('\n');
        sb.append("KILL 파일: ").append(r.killFile()).append('\n');
        sb.append("감사로그 이벤트 수: ")
                .append(r.auditEvents() < 0 ? "읽기 실패" : String.valueOf(r.auditEvents()))
                .append('\n');
        sb.append("web 포트: ")
                .append(r.webPort())
                .append(" (127.0.0.1, ")
                .append(r.webPortState())
                .append(")\n");
        return sb.toString();
    }
}
