package io.github.psung616.tossmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * web 모드 설정 (ARCHITECTURE §4). 주소·포트는 {@code server.*} 로 고정·검사되므로 여기 두지 않는다.
 *
 * @param openBrowser 기동 시 기본 브라우저로 접속 URL 열기 ({@code TOSSINVEST_WEB_OPEN_BROWSER}, 기본 true — RC-002 D3)
 */
@ConfigurationProperties(prefix = "tossinvest.web")
public record WebProperties(@DefaultValue("true") boolean openBrowser) {}
