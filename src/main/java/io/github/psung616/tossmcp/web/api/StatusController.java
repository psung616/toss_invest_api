package io.github.psung616.tossmcp.web.api;

import io.github.psung616.tossmcp.service.AppStatusService;
import io.github.psung616.tossmcp.service.AppStatusService.AppStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/status} (ARCHITECTURE §12.3, RC-002 D5 로 P9-2 와 함께). 세션이 있어야 닿는다({@code LocalAccessFilter}). */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class StatusController {

    private final AppStatusService status;

    public StatusController(AppStatusService status) {
        this.status = status;
    }

    @GetMapping(path = "/api/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public AppStatus status() {
        return status.status();
    }
}
