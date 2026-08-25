package com.lczz.auth.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.config.WechatMiniProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatApiClientTests {

    @Test
    void acceptsWechatJsonContentTypeAndIgnoresSessionKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatApiClient client = new WechatApiClient(builder.build(), new ObjectMapper(),
                new WechatMiniProperties("wx-test", "secret-test"), Clock.systemUTC());

        server.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test&secret=secret-test&js_code=login-code&grant_type=authorization_code"))
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
}
