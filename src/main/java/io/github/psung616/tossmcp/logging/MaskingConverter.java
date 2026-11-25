package io.github.psung616.tossmcp.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** logback 패턴의 {@code %maskedMsg}: 포맷된 로그 메시지를 {@link Masking} 으로 가린다. */
public class MaskingConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return Masking.mask(event.getFormattedMessage());
    }
}
