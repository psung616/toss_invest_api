package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /api/v1/accounts 의 result 원소. 계좌번호(accountNo)는 필요 없으므로 받지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(long accountSeq, String accountType) {

    @Override
    public String toString() {
        return "AccountDto[accountSeq=****, accountType=" + accountType + "]";
    }
}
