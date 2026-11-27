package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.service.AccountQueryService;
import io.github.psung616.tossmcp.service.QuoteService;
import io.github.psung616.tossmcp.service.StockSearchService;
import io.github.psung616.tossmcp.stock.StockMasterCache;

/** 도구 조립 — 실제 서비스 + 가짜 {@code TossApi}. 서비스 추출(P9-3) 전후로 테스트 본문은 그대로 두고 여기만 바꿨다. */
final class ToolFixtures {

    private ToolFixtures() {}

    static MarketTools marketTools(TossApi tossApi, StockMasterCache stockMaster) {
        return new MarketTools(new QuoteService(tossApi), new StockSearchService(stockMaster));
    }

    static AccountTools accountTools(TossApi tossApi) {
        return new AccountTools(new AccountQueryService(tossApi));
    }
}
