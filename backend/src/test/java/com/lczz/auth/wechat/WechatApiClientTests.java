package com.lczz.auth.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.config.WechatMiniProperties;
import com.lczz.common.exception.BusinessException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(OutputCaptureExtension.class)
class WechatApiClientTests {

    @Test
    void acceptsWechatJsonContentTypeAndIgnoresSessionKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatApiClient client = new WechatApiClient(builder.build(), new ObjectMapper(),
                new WechatMiniProperties("wx-test", "secret-test"), Clock.systemUTC());

        server.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test&secret=secret-test&js_code=login-code&grant_type=authorization_code"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.USER_AGENT, "LCZZ-Backend/0.1"))
                .andRespond(withSuccess("""
                        {"openid":"openid-1","session_key":"session-key-1","unionid":"unionid-1"}
                        """, MediaType.parseMediaType("application/json;encoding=utf-8")));

        WechatIdentity identity = client.exchangeLoginCode("login-code");

        assertThat(identity).isEqualTo(new WechatIdentity("wx-test", "openid-1", "unionid-1"));
        server.verify();
    }

    @Test
    void acceptsWechatJsonWithUnknownContentType() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatApiClient client = new WechatApiClient(builder.build(), new ObjectMapper(),
                new WechatMiniProperties("wx-test", "secret-test"), Clock.systemUTC());

        server.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test&secret=secret-test&js_code=login-code&grant_type=authorization_code"))
                .andRespond(withSuccess("""
                        {"openid":"openid-2","session_key":"session-key-2"}
                        """, MediaType.APPLICATION_OCTET_STREAM));

        WechatIdentity identity = client.exchangeLoginCode("login-code");

        assertThat(identity).isEqualTo(new WechatIdentity("wx-test", "openid-2", null));
        server.verify();
    }

    @Test
    void sendsWechatCompatibleHeadersWhenExchangingPhoneCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatApiClient client = new WechatApiClient(builder.build(), new ObjectMapper(),
                new WechatMiniProperties("wx-test", "secret-test"), Clock.systemUTC());

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token"
                        + "?grant_type=client_credential&appid=wx-test&secret=secret-test"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.USER_AGENT, "LCZZ-Backend/0.1"))
                .andRespond(withSuccess("""
                        {"access_token":"access-token-1","expires_in":7200}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber"
                        + "?access_token=access-token-1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.USER_AGENT, "LCZZ-Backend/0.1"))
                .andExpect(content().json("{\"code\":\"phone-code-1\"}"))
                .andRespond(withSuccess("""
                        {"errcode":0,"errmsg":"ok","phone_info":{"phoneNumber":"13800138000"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.exchangePhoneCode("phone-code-1")).isEqualTo("13800138000");
        server.verify();
    }

    @Test
    void identifiesAccessTokenHttpFailureSeparately(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatApiClient client = new WechatApiClient(builder.build(), new ObjectMapper(),
                new WechatMiniProperties("wx-test", "secret-test"), Clock.systemUTC());

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token"
                        + "?grant_type=client_credential&appid=wx-test&secret=secret-test"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        assertThatThrownBy(() -> client.exchangePhoneCode("phone-code-1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo("WECHAT_UNAVAILABLE");
                });
        assertThat(output).contains("operation=access-token")
                .doesNotContain("operation=phone-number");
        server.verify();
    }

    @Test
    void identifiesPhoneNumberHttpFailureAfterTokenSucceeds(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatApiClient client = new WechatApiClient(builder.build(), new ObjectMapper(),
                new WechatMiniProperties("wx-test", "secret-test"), Clock.systemUTC());

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token"
                        + "?grant_type=client_credential&appid=wx-test&secret=secret-test"))
                .andRespond(withSuccess("""
                        {"access_token":"access-token-1","expires_in":7200}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber"
                        + "?access_token=access-token-1"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        assertThatThrownBy(() -> client.exchangePhoneCode("phone-code-1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo("WECHAT_UNAVAILABLE");
                });
        assertThat(output).contains("operation=phone-number")
                .doesNotContain("operation=access-token");
        server.verify();
    }
}
