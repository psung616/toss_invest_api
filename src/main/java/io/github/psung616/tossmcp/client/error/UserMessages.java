package io.github.psung616.tossmcp.client.error;

import java.util.Map;

/**
 * 에러 코드 → 한국어 안내 + 다음 행동.
 *
 * TODO(P1-6): TOSS_API_NOTES 의 전체 에러 코드 매핑
 */
public final class UserMessages {

    private static final Map<String, String> MESSAGES = Map.of(
            "ip-not-allowed", "허용되지 않은 IP 입니다. 토스증권 WTS > 설정 > Open API > 허용 IP 에 현재 PC 의 공인 IP 를 등록하세요.",
            "access_denied", "토큰 발급이 거부되었습니다. 키가 올바른지, 허용 IP 가 등록되었는지 확인하세요.",
            "insufficient-buying-power", "매수 가능 금액이 부족합니다. get_buying_power 로 가능 금액을 확인한 뒤 수량을 줄여 다시 미리보기 하세요.",
            "order-hours-closed", "지금은 주문 가능 시간이 아닙니다.",
            "price-out-of-range", "주문 가격이 상·하한가 범위를 벗어났습니다.",
            "stock-not-found", "종목을 찾을 수 없습니다. 종목 코드를 확인하세요.",
            "rate-limit-exceeded", "요청이 너무 많습니다. 잠시 후 다시 시도하세요.",
            "maintenance", "토스증권 시스템 점검 중입니다. 점검이 끝난 뒤 다시 시도하세요.");

    private UserMessages() {}

    public static String of(String code) {
        if (code == null) {
            return "토스증권 API 요청이 실패했습니다.";
        }
        return MESSAGES.getOrDefault(code, "토스증권 API 요청이 실패했습니다. (code: " + code + ")");
    }
}
