package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.client.auth.TokenCacheFile;
import io.github.psung616.tossmcp.client.auth.TokenIssuer;
import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.config.AppPaths;
import io.github.psung616.tossmcp.config.KillSwitch;
import io.github.psung616.tossmcp.config.TossProperties;
import io.github.psung616.tossmcp.secret.CredentialStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** diagnose 테스트 조립 (service·cli 테스트 공용). 임시 폴더만 쓴다. */
public final class DiagnosticsServiceTestSupport {

    private DiagnosticsServiceTestSupport() {}

    public static TossProperties props(boolean enableOrders, boolean dryRun) {
        return new TossProperties(
                enableOrders,
                dryRun,
                new BigDecimal("100000"),
                new BigDecimal("300000"),
                10,
                List.of(),
                false,
                false,
                false,
                5,
                Duration.ofMinutes(5));
    }

    public static DiagnosticsService service(Path dir, CredentialStore credentials, TokenIssuer issuer) {
        AppPaths paths = new AppPaths(dir.resolve("config"), dir.resolve("cache"), dir.resolve("logs"));
        try {
            Files.createDirectories(paths.configDir());
            Files.createDirectories(paths.cacheDir());
            Files.createDirectories(paths.logsDir());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        TokenManager tokens = new TokenManager(
                issuer, credentials, new TokenCacheFile(paths.cacheDir().resolve("token.json")), Clock.systemUTC());
        AppStatusService status = new AppStatusService(credentials, props(false, true), "0.1.0", () -> false);
        return new DiagnosticsService(status, props(false, true), paths, new KillSwitch(paths), tokens, 8765);
    }
}
