package io.github.psung616.tossmcp.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * logback 패턴의 {@code %maskedEx}: 예외 메시지·스택트레이스를 {@link Masking} 으로 가린다.
 *
 * <p>패턴에 예외 변환기가 없으면 logback 이 마스킹 없는 기본 변환기를 자동으로 붙이므로, 모든 패턴 끝에 반드시 이것을 둔다.
 */
public class MaskingThrowableConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return Masking.mask(super.convert(event));
    }
}
