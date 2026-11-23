package io.github.psung616.tossmcp.order;

import io.github.psung616.tossmcp.config.TossProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 미리보기된 주문을 previewId 로 잠시 보관한다. previewId 는 1회용이며 만료 시간이 있다.
 */
@Component
public class PreviewStore {

    private final Map<String, Preview> previews = new ConcurrentHashMap<>();
    private final Clock clock;
    private final TossProperties properties;

    public PreviewStore(Clock clock, TossProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public Preview save(OrderCommand command, String currency, BigDecimal unitPrice, BigDecimal estimatedKrw) {
        Instant now = clock.instant();
        previews.values().removeIf(p -> p.isExpired(now));
        Preview preview = new Preview(
                UUID.randomUUID().toString(),
                command,
                currency,
                unitPrice,
                estimatedKrw,
                now.plus(properties.previewTtl()));
        previews.put(preview.previewId(), preview);
        return preview;
    }

    /** 꺼내면서 삭제한다. 같은 previewId 로 두 번 주문되지 않는다. */
    public Optional<Preview> take(String previewId) {
        if (previewId == null) {
            return Optional.empty();
        }
        Preview preview = previews.remove(previewId.trim());
        if (preview == null || preview.isExpired(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(preview);
    }

    public record Preview(
            String previewId,
            OrderCommand command,
            String currency,
            BigDecimal unitPrice,
            BigDecimal estimatedKrw,
            Instant expiresAt) {

        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}
