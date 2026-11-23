package io.github.psung616.tossmcp.client.auth;

import io.github.psung616.tossmcp.config.AppPaths;
import io.github.psung616.tossmcp.secret.CredentialStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    @Bean
    TokenCacheFile tokenCacheFile(AppPaths appPaths) {
        return new TokenCacheFile(appPaths.cacheDir().resolve("token.json"));
    }

    @Bean
    TokenManager tokenManager(TokenIssuer issuer, CredentialStore credentialStore, TokenCacheFile cache, Clock clock) {
        return new TokenManager(issuer, credentialStore, cache, clock);
    }
}
