package io.github.psung616.tossmcp.web.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

/** web 모드 바인딩 검사 (RC-001, ARCHITECTURE §12.1-3). DNS 조회 없이 주소를 바이트로 만든다. */
class LoopbackGuardTest {

    private final LoopbackGuard guard = new LoopbackGuard();

    private static TomcatServletWebServerFactory factory(InetAddress address, int port) {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setAddress(address);
        factory.setPort(port);
        return factory;
    }

    private static InetAddress ip(int... bytes) throws Exception {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            b[i] = (byte) bytes[i];
        }
        return InetAddress.getByAddress(b);
    }

    @Test
    void allowsIpv4Loopback() throws Exception {
        assertThatCode(() -> guard.customize(factory(ip(127, 0, 0, 1), 8765))).doesNotThrowAnyException();
    }

    @Test
    void allowsIpv6Loopback() throws Exception {
        assertThatCode(() -> guard.customize(factory(ip(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1), 8765)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAllInterfaces() throws Exception {
        assertThatThrownBy(() -> guard.customize(factory(ip(0, 0, 0, 0), 8765)))
                .isInstanceOf(LoopbackViolationException.class);
    }

    @Test
    void rejectsMissingAddressBecauseTomcatWouldListenOnAllInterfaces() {
        assertThatThrownBy(() -> guard.customize(factory(null, 8765))).isInstanceOf(LoopbackViolationException.class);
    }

    @Test
    void rejectsLanAddress() throws Exception {
        assertThatThrownBy(() -> guard.customize(factory(ip(192, 168, 0, 10), 8765)))
                .isInstanceOf(LoopbackViolationException.class)
                .hasMessageContaining("127.0.0.1");
    }

    @Test
    void rejectsPrivilegedPort() throws Exception {
        assertThatThrownBy(() -> guard.customize(factory(ip(127, 0, 0, 1), 80)))
                .isInstanceOf(LoopbackViolationException.class)
                .hasMessageContaining("1024");
    }

    @Test
    void allowsEphemeralPortZero() throws Exception {
        assertThatCode(() -> guard.customize(factory(ip(127, 0, 0, 1), 0))).doesNotThrowAnyException();
    }

    @Test
    void boundAddressMustBeLoopback() throws Exception {
        assertThatCode(() -> LoopbackGuard.checkBoundAddress(ip(127, 0, 0, 1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> LoopbackGuard.checkBoundAddress(ip(10, 0, 0, 5)))
                .isInstanceOf(LoopbackViolationException.class);
        assertThatThrownBy(() -> LoopbackGuard.checkBoundAddress(null)).isInstanceOf(LoopbackViolationException.class);
    }
}
