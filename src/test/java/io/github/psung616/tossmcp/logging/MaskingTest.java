package io.github.psung616.tossmcp.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 모든 값은 지어낸 가짜 값이다. */
class MaskingTest {

    @Test
    void bearerToken() {
        assertThat(Masking.mask("Authorization: Bearer eyJhbGciOi.fake-payload_123.sig=="))
                .isEqualTo("Authorization: Bearer ***");
    }

    @Test
    void formEncodedCredentials() {
        assertThat(Masking.mask("grant_type=client_credentials&client_id=abc123&client_secret=s3cr3t"))
                .isEqualTo("grant_type=client_credentials&client_id=***&client_secret=***");
    }

    @Test
    void clientIdPrefixAnywhere() {
        assertThat(Masking.mask("키 확인 실패: tsck_live_fake-client-id 거부됨")).isEqualTo("키 확인 실패: tsck_*** 거부됨");
    }

    @Test
    void accountHeader() {
        assertThat(Masking.mask("X-Tossinvest-Account: 12")).isEqualTo("X-Tossinvest-Account: ***");
        assertThat(Masking.mask("headers={X-Tossinvest-Account=12, Accept=json}"))
                .isEqualTo("headers={X-Tossinvest-Account=***, Accept=json}");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "{\"access_token\":\"fake.jwt.value\",\"token_type\":\"Bearer\"}|{\"access_token\":\"***\",\"token_type\":\"***\"}",
                "{\"accountSeq\": 3, \"accountType\":\"BROKERAGE\"}|{\"accountSeq\":\"***\", \"accountType\":\"BROKERAGE\"}",
                "{\"symbol\":\"005930\",\"lastPrice\":\"70000\"}|{\"symbol\":\"005930\",\"lastPrice\":\"***\"}",
                "{\"quantity\":\"10\",\"price\":\"70000\",\"orderAmount\":null}|{\"quantity\":\"***\",\"price\":\"***\",\"orderAmount\":\"***\"}",
                "{\"cashBuyingPower\":\"1234567\",\"currency\":\"KRW\"}|{\"cashBuyingPower\":\"***\",\"currency\":\"KRW\"}",
                "{\"totalBalance\":-12.5}|{\"totalBalance\":\"***\"}",
                "{\"client_secret\":\"a\\\"b\"}|{\"client_secret\":\"***\"}",
            })
    void jsonSensitiveKeys(String input, String expected) {
        assertThat(Masking.mask(input)).isEqualTo(expected);
    }

    @Test
    void recordToStringStyle() {
        assertThat(Masking.mask("TokenResponse[accessToken=fake.jwt, tokenType=Bearer, expiresIn=86400]"))
                .isEqualTo("TokenResponse[accessToken=***, tokenType=***, expiresIn=86400]");
        assertThat(Masking.mask("OrderCommand[symbol=005930, quantity=3, price=71000]"))
                .isEqualTo("OrderCommand[symbol=005930, quantity=***, price=***]");
    }

    @Test
    void ordinaryTextUnchanged() {
        String text = "종목 마스터 갱신: KOSPI 950개, 다음 개장 09:00 (status=429, code=rate-limit-exceeded)";
        assertThat(Masking.mask(text)).isEqualTo(text);
    }

    @Test
    void nullAndEmpty() {
        assertThat(Masking.mask(null)).isNull();
        assertThat(Masking.mask("")).isEmpty();
    }

    /** web 모드 세션 쿠키·접속 코드 (WEB_SECURITY WS-LOG-2). */
    @Test
    void webSessionCookieAndLaunchCode() {
        String code = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_abcde";
        assertThat(Masking.mask("GET /login?code=" + code + " HTTP/1.1")).isEqualTo("GET /login?code=*** HTTP/1.1");
        assertThat(Masking.mask("tim_session=" + code + "; Path=/")).isEqualTo("tim_session=***; Path=/");
        assertThat(Masking.mask("Cookie: tim_session=abc; other=1")).isEqualTo("Cookie: ***");
        assertThat(Masking.mask("Set-Cookie: tim_session=abc; HttpOnly")).isEqualTo("Set-Cookie: ***");
    }

    /** 토스 에러 코드(짧은 code=값)는 진단에 필요하므로 가리지 않는다. */
    @Test
    void shortErrorCodesStayVisible() {
        assertThat(Masking.mask("Toss API error status=401 code=invalid_client requestId=r-1"))
                .isEqualTo("Toss API error status=401 code=invalid_client requestId=r-1");
    }
}
