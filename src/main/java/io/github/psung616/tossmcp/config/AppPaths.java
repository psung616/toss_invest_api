package io.github.psung616.tossmcp.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 토큰 캐시·로그·카운터를 저장할 OS 사용자 폴더. 저장소 폴더에는 절대 쓰지 않는다.
 * 테스트에서는 시스템 프로퍼티 {@code tossinvest.home} 으로 루트를 바꿀 수 있다.
 *
 * TODO(P1-1): OS별 경로 계산 테스트, 디렉터리 권한
 */
public record AppPaths(Path configDir, Path cacheDir, Path logsDir) {

    private static final String APP = "tossinvest-mcp";

    public static AppPaths detect() {
        String home = System.getProperty("tossinvest.home");
        if (home != null && !home.isBlank()) {
            Path root = Path.of(home);
            return create(root.resolve("config"), root.resolve("cache"), root.resolve("logs"));
        }

        Path userHome = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            Path roaming = envPath("APPDATA", userHome.resolve("AppData/Roaming"));
            Path local = envPath("LOCALAPPDATA", userHome.resolve("AppData/Local"));
            return create(
                    roaming.resolve(APP),
                    local.resolve(APP).resolve("cache"),
                    local.resolve(APP).resolve("logs"));
        }
        if (os.contains("mac")) {
            Path lib = userHome.resolve("Library");
            return create(
                    lib.resolve("Application Support").resolve(APP),
                    lib.resolve("Caches").resolve(APP),
                    lib.resolve("Logs").resolve(APP));
        }
        Path config = envPath("XDG_CONFIG_HOME", userHome.resolve(".config"));
        Path cache = envPath("XDG_CACHE_HOME", userHome.resolve(".cache"));
        Path state = envPath("XDG_STATE_HOME", userHome.resolve(".local/state"));
        return create(
                config.resolve(APP), cache.resolve(APP), state.resolve(APP).resolve("logs"));
    }

    private static AppPaths create(Path config, Path cache, Path logs) {
        try {
            Files.createDirectories(config);
            Files.createDirectories(cache);
            Files.createDirectories(logs);
        } catch (IOException e) {
            throw new UncheckedIOException("사용자 폴더를 만들 수 없습니다: " + e.getMessage(), e);
        }
        return new AppPaths(config, cache, logs);
    }

    private static Path envPath(String name, Path fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Path.of(value);
    }
}
