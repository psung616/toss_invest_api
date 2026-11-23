package io.github.psung616.tossmcp.client.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사용자 cache 폴더의 token.json. 토스증권은 클라이언트당 유효 토큰이 1개라서, 같은 키를 쓰는 프로세스
 * (Claude 용 serve, 디스코드 봇, setup 연결 테스트)가 이 파일로 토큰을 공유해야 서로 무효화하지 않는다.
 *
 * <p>잠금: 같은 JVM 안은 경로별 {@link ReentrantLock}, 프로세스 사이는 token.json.lock 의 {@link FileLock}.
 * Client ID 원문은 저장하지 않고 SHA-256 해시만 둔다 (키가 바뀌면 캐시 무시).
 */
public class TokenCacheFile {

    private static final Map<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private final Path file;
    private final Path lockFile;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public TokenCacheFile(Path file) {
        this.file = file.toAbsolutePath();
        this.lockFile = this.file.resolveSibling(this.file.getFileName() + ".lock");
    }

    public record CachedToken(String accessToken, Instant expiresAt, String clientIdHash) {

        @Override
        public String toString() {
            return "CachedToken[accessToken=****, expiresAt=" + expiresAt + "]";
        }
    }

    /** 프로세스 간 배타 잠금 안에서 action 을 실행한다. */
    public <T> T withLock(Supplier<T> action) {
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(file, p -> new ReentrantLock());
        jvmLock.lock();
        try {
            Files.createDirectories(file.getParent());
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                return action.get();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("토큰 캐시 잠금 실패: " + e.getMessage(), e);
        } finally {
            jvmLock.unlock();
        }
    }

    /** 잠금 안에서 호출할 것. 파일이 없거나 깨졌거나 다른 키의 토큰이면 empty. */
    public Optional<CachedToken> read(String clientId) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            CachedToken token = jsonMapper.readValue(Files.readAllBytes(file), CachedToken.class);
            if (token == null || token.accessToken() == null || token.expiresAt() == null) {
                return Optional.empty();
            }
            return hash(clientId).equals(token.clientIdHash()) ? Optional.of(token) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** 잠금 안에서 호출할 것. 임시 파일에 쓰고 원자적으로 교체한다. */
    public void write(String clientId, String accessToken, Instant expiresAt) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            byte[] json = jsonMapper.writeValueAsBytes(new CachedToken(accessToken, expiresAt, hash(clientId)));
            Files.write(tmp, json);
            restrictPermissions(tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("토큰 캐시 저장 실패: " + e.getMessage(), e);
        }
    }

    /** 잠금 안에서 호출할 것. */
    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("토큰 캐시 삭제 실패: " + e.getMessage(), e);
        }
    }

    private static void restrictPermissions(Path path) {
        try {
            // POSIX(macOS/Linux)에서는 소유자만 읽기·쓰기. Windows 는 사용자 프로필 폴더 기본 ACL 에 의존한다.
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows
        }
    }

    static String hash(String clientId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(clientId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
