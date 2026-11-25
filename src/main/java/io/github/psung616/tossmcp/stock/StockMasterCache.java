package io.github.psung616.tossmcp.stock;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.StockListItemDto;
import io.github.psung616.tossmcp.config.AppPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 거래 가능 종목 전체 목록을 사용자 cache 폴더(stock-master.json)에 24시간 보관한다.
 * 토스 문서 권장: 일 배치 갱신 데이터이므로 하루 1회 조회 후 로컬 캐싱.
 * 이 파일은 API 데이터이므로 저장소에 절대 커밋하지 않는다 (사용자 폴더에만 존재).
 *
 * 마켓 간 간격(STOCK_ALL 초당 1회)은 TossHttpClient 의 레이트리미터가 맞춘다.
 */
@Component
public class StockMasterCache {

    static final List<String> MARKETS = List.of("KOSPI", "KOSDAQ", "NASDAQ", "NYSE", "AMEX");
    private static final Duration TTL = Duration.ofHours(24);
    private static final Logger log = LoggerFactory.getLogger(StockMasterCache.class);

    private final TossApi tossApi;
    private final Path file;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private volatile StockSearch search;
    private volatile Instant loadedAt = Instant.EPOCH;

    public StockMasterCache(TossApi tossApi, AppPaths appPaths, Clock clock) {
        this.tossApi = tossApi;
        this.file = appPaths.cacheDir().resolve("stock-master.json");
        this.clock = clock;
    }

    record Snapshot(Instant fetchedAt, List<StockEntry> entries) {}

    public synchronized StockSearch search() {
        Instant now = clock.instant();
        if (search != null && now.isBefore(loadedAt.plus(TTL))) {
            return search;
        }
        Snapshot snapshot = readFile();
        if (snapshot == null || !now.isBefore(snapshot.fetchedAt().plus(TTL))) {
            snapshot = new Snapshot(now, fetchAll());
            writeFile(snapshot);
        }
        search = new StockSearch(snapshot.entries());
        loadedAt = snapshot.fetchedAt();
        return search;
    }

    private List<StockEntry> fetchAll() {
        List<StockEntry> all = new ArrayList<>();
        for (String market : MARKETS) {
            for (StockListItemDto s : tossApi.listStocks(market)) {
                if (s.symbol() != null && s.name() != null) {
                    all.add(new StockEntry(
                            s.symbol(), s.name(), market, s.securityType(), !Boolean.FALSE.equals(s.isCommonShare())));
                }
            }
        }
        log.info("Stock master refreshed: {} entries", all.size());
        return all;
    }

    private Snapshot readFile() {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            Snapshot s = jsonMapper.readValue(Files.readAllBytes(file), Snapshot.class);
            return s == null || s.entries() == null || s.fetchedAt() == null ? null : s;
        } catch (IOException | RuntimeException e) {
            log.warn("Stock master cache unreadable, refetching: {}", e.getMessage());
            return null;
        }
    }

    private void writeFile(Snapshot snapshot) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, jsonMapper.writeValueAsBytes(snapshot));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Stock master cache not saved: {}", e.getMessage());
        }
    }
}
