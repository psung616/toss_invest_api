package io.github.psung616.tossmcp;

import io.github.psung616.tossmcp.cli.Usage;
import io.github.psung616.tossmcp.config.AppPaths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TossMcpApplication {

    public static void main(String[] args) {
        String mode = args.length == 0 ? "--help" : args[0];
        switch (mode) {
            case "serve" -> serve();
            case "setup", "reset-keys", "diagnose" -> System.exit(Usage.notImplemented(mode));
            default -> Usage.print();
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
