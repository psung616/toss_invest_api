package io.github.psung616.tossmcp.web.security;

import java.net.InetAddress;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.AbstractConfigurableWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * web 모드 바인딩 검사 (RC-001, ARCHITECTURE §12.1-3). 같은 네트워크·인터넷에서 접근할 수 없어야 한다.
 *
 * <ol>
 *   <li>기동 전: 웹 서버 팩토리의 주소가 루프백이 아니면(주소 미지정 = 모든 인터페이스 포함) 기동을 거부한다.
 *       다른 커스터마이저가 모두 적용된 뒤 검사하도록 가장 마지막 순서로 실행한다.
 *   <li>기동 직후: Tomcat 커넥터가 실제로 바인딩한 주소를 다시 확인한다(이중 방어).
 * </ol>
 *
 * 실패하면 {@link LoopbackViolationException} 으로 컨텍스트 기동이 중단된다. serve 등 웹이 아닌 모드에서는 빈이 만들어지지 않는다.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LoopbackGuard
        implements WebServerFactoryCustomizer<ConfigurableWebServerFactory>,
                ApplicationListener<WebServerInitializedEvent>,
                Ordered {

    static final int MIN_PORT = 1024;
    static final int MAX_PORT = 65535;

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        if (!(factory instanceof AbstractConfigurableWebServerFactory configurable)) {
            throw new LoopbackViolationException("바인딩 주소를 확인할 수 없는 웹 서버라 시작하지 않습니다.");
        }
        checkBoundAddress(configurable.getAddress());
        checkPort(configurable.getPort());
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (!(event.getWebServer() instanceof TomcatWebServer tomcat)) {
            throw new LoopbackViolationException("바인딩 주소를 확인할 수 없는 웹 서버라 시작하지 않습니다.");
        }
        for (Connector connector : tomcat.getTomcat().getService().findConnectors()) {
            checkBoundAddress(connector.getProperty("address") instanceof InetAddress address ? address : null);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void checkBoundAddress(InetAddress address) {
        if (address == null || !address.isLoopbackAddress()) {
            throw new LoopbackViolationException("web 모드는 내 PC(127.0.0.1) 에서만 실행합니다. 다른 주소로는 시작하지 않습니다.");
        }
    }

    static void checkPort(int port) {
        // 0 은 운영체제가 고르는 임의 포트(테스트용). 그 외에는 1024~65535 만 허용한다.
        if (port != 0 && (port < MIN_PORT || port > MAX_PORT)) {
            throw new LoopbackViolationException(
                    "TOSSINVEST_WEB_PORT 는 " + MIN_PORT + "~" + MAX_PORT + " 사이여야 합니다 (현재 " + port + ").");
        }
    }
}
