package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.config.TossProperties;
import io.github.psung616.tossmcp.secret.CredentialStore;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 앱 상태 (WEB_SECURITY WS-DATA-2): 버전, 키 <b>설정 여부</b>·환경변수 키 여부, 주문 기능·DRY_RUN 여부만. 키 값은 절대 담지 않는다. web 은 secret 을
 * 직접 쓰지 않고 이 서비스를 거친다(ARCHITECTURE §10).
 */
@Service
public class AppStatusService {

    /** @param keysFromEnvironment 환경변수 키가 쓰이는 중 — 화면에서 키를 저장할 수 없다(RC-003) */
    public record AppStatus(
            String version,
            boolean keysConfigured,
            boolean keysFromEnvironment,
            boolean ordersEnabled,
            boolean dryRun) {}

    private final CredentialStore credentials;
    private final TossProperties properties;
    private final String version;
    private final BooleanSupplier envKeysPresent;

    @Autowired
    public AppStatusService(
            CredentialStore credentials,
            TossProperties properties,
            @Value("${spring.ai.mcp.server.version}") String version) {
        this(credentials, properties, version, KeySetupService::envKeysPresent);
    }

    public AppStatusService(
            CredentialStore credentials, TossProperties properties, String version, BooleanSupplier envKeysPresent) {
        this.credentials = credentials;
        this.properties = properties;
        this.version = version;
        this.envKeysPresent = envKeysPresent;
    }

    public AppStatus status() {
        return new AppStatus(
                version,
                credentials.load().isPresent(),
                envKeysPresent.getAsBoolean(),
                properties.enableOrders(),
                properties.dryRun());
    }
}
