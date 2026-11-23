package io.github.psung616.tossmcp.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * 환경변수 TOSSINVEST_KILL=1 이거나 사용자 config 폴더에 KILL 파일이 있으면 모든 주문을 거부한다.
 */
@Component
public class KillSwitch {

    private final Path killFile;

    public KillSwitch(AppPaths appPaths) {
        this.killFile = appPaths.configDir().resolve("KILL");
    }

    public boolean isActive() {
        return "1".equals(System.getenv("TOSSINVEST_KILL")) || Files.exists(killFile);
    }

    public Path killFile() {
        return killFile;
    }
}
