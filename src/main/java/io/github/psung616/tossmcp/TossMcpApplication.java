package io.github.psung616.tossmcp;

import io.github.psung616.tossmcp.cli.Cli;
import io.github.psung616.tossmcp.cli.ConsoleEncoding;
import io.github.psung616.tossmcp.cli.DiagnoseCommand;
import io.github.psung616.tossmcp.cli.Usage;
import io.github.psung616.tossmcp.cli.WebLauncher;
import io.github.psung616.tossmcp.config.AppPaths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TossMcpApplication {

    public static void main(String[] args) {
        if (args.length == 0 || !"serve".equals(args[0])) {
            // CLI 모드만: 파이프(Git Bash·Claude Code `!`)로 받을 때 한글이 깨지지 않게 UTF-8. serve 의 stdout 은 MCP 전용이라 건드리지 않는다.
            ConsoleEncoding.useUtf8WhenRedirected();
        }
        if (args.length == 0) {
            Cli.noArgs();
            return;
        }
        switch (args[0]) {
            case "serve" -> serve();
            case "web" -> web();
            case "setup" -> exitIfFailed(Cli.setup(args.length > 1 && "--gui".equals(args[1])));
            case "reset-keys" -> System.exit(Cli.resetKeys());
            case "diagnose" -> diagnose(args);
            default -> Usage.print();
        }
    }

    private static void exitIfFailed(int code) {
        // 창(GUI) 모드는 창이 닫힐 때 종료되므로 성공 시에는 exit 하지 않는다.
        if (code != 0) {
            System.exit(code);
        }
    }

    /** 외부 설정 파일(.env, 작업 폴더 application.yml, config/)을 읽지 않도록 번들 설정만 사용하고, 로그는 사용자 폴더로 보낸다. */
    private static void lockConfiguration() {
        System.setProperty("spring.config.location", "classpath:/application.yml");
        System.setProperty("tossinvest.logs", AppPaths.detect().logsDir().toString());
    }

    private static void serve() {
        lockConfiguration();
        System.setProperty("spring.ai.mcp.server.stdio", "true");

        // Spring 에 명령행 인자를 넘기지 않는다 (--spring.* 로 설정을 바꾸지 못하게).
        SpringApplication.run(TossMcpApplication.class);
    }

    /** 민감정보 없는 진단 출력 (P3-5). {@code diagnose --offline} 은 토스 API 를 부르지 않는다. */
    private static void diagnose(String[] args) {
        lockConfiguration();
        System.exit(DiagnoseCommand.run(args, () -> SpringApplication.run(TossMcpApplication.class)));
    }

    /** 127.0.0.1 전용 웹 서버 (RC-001). 명령행 인자는 serve 와 마찬가지로 Spring 에 넘기지 않는다. */
    private static void web() {
        lockConfiguration();
        exitIfFailed(WebLauncher.run(() -> SpringApplication.run(TossMcpApplication.class)));
    }
}
