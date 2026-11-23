package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 실패 응답 봉투 {@code {"error": {requestId, code, message, data}}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorEnvelope(ErrorBody error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(String requestId, String code, String message) {}
}
