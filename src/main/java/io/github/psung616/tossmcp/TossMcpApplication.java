package io.github.psung616.tossmcp;

import io.github.psung616.tossmcp.cli.Cli;
import io.github.psung616.tossmcp.cli.Usage;
import io.github.psung616.tossmcp.config.AppPaths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TossMcpApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            Cli.noArgs();
            return;
        }
        switch (args[0]) {
            case "serve" -> serve();
            case "setup" -> exitIfFailed(Cli.setup(args.length > 1 && "--gui".equals(args[1])));
            case "reset-keys" -> System.exit(Cli.resetKeys());
            case "diagnose" -> System.exit(Usage.notImplemented("diagnose"));
            default -> Usage.print();
        }
    }

    private static void exitIfFailed(int code) {
        // 창(GUI) 모드는 창이 닫힐 때 종료되므로 성공 시에는 exit 하지 않는다.
        if (code != 0) {
            System.exit(code);
        }
    }

    private static void serve() {
        // 외부 설정 파일(.env, 작업 폴더 application.yml, config/)을 읽지 않도록 번들 설정만 사용한다.
        System.setProperty("spring.config.location", "classpath:/application.yml");
        System.setProperty("spring.ai.mcp.server.stdio", "true");
        System.setProperty("tossinvest.logs", AppPaths.detect().logsDir().toString());

        // Spring 에 명령행 인자를 넘기지 않는다 (--spring.* 로 설정을 바꾸지 못하게).
        SpringApplication.run(TossMcpApplication.class);
    }
}
