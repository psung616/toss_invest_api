package io.github.psung616.tossmcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.http.TossHttpClient;
import io.github.psung616.tossmcp.client.ratelimit.RateLimiter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 스프링 빈이 모두 연결되는지 확인한다(생성자 인자 누락 등). 네트워크는 쓰지 않는다 — 기동 시 토스 API 를 부르지 않으며, 사용자 폴더 대신 임시
 * 폴더(tossinvest.home)를 쓴다.
 */
class ApplicationContextTest {

    private static Path home;
    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void start() throws Exception {
        home = Files.createTempDirectory("tossmcp-ctx");
        System.setProperty("tossinvest.home", home.toString());
        SpringApplication app = new SpringApplication(TossMcpApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        context = app.run("--spring.ai.mcp.server.stdio=false", "--logging.level.root=WARN");
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
        System.clearProperty("tossinvest.home");
    }

    @Test
    void wiresHttpPipeline() {
        assertThat(context.getBean(TossHttpClient.class)).isNotNull();
        assertThat(context.getBean(TossApi.class)).isNotNull();
        assertThat(context.getBean(RateLimiter.class)).isNotNull();
    }
}
