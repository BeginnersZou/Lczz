package com.lczz.auth.wechat;

public interface WechatIdentityGateway {
    WechatIdentity exchangeLoginCode(String code);
    String exchangePhoneCode(String phoneCode);
}
