package io.github.psung616.tossmcp.secret;

import java.util.Optional;

/**
 * API 키 조회. 구현: 환경변수(우선) → OS 자격 증명 저장소(P1-3, java-keyring).
 */
public interface CredentialStore {

    Optional<Credentials> load();
}
