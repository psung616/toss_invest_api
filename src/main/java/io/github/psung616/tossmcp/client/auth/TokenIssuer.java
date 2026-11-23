package io.github.psung616.tossmcp.client.auth;

import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.secret.Credentials;

/** 토큰 발급자. 실제 구현은 client.http.TokenEndpoint (POST /oauth2/token). */
public interface TokenIssuer {

    TokenResponse issue(Credentials credentials);
}
