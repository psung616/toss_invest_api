package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 성공 응답 봉투 {@code {"result": ...}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Envelope<T>(T result) {}
