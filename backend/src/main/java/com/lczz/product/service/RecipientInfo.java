package com.lczz.product.service;

import com.lczz.common.exception.BusinessException;

/** Values copied into an order, independent of future address-book changes. */
public record RecipientInfo(String recipientName, String recipientPhone, String recipientAddress) {
    public static RecipientInfo validated(RecipientInfo input) {
        String name = input == null ? "" : clean(input.recipientName());
        String phone = input == null ? "" : clean(input.recipientPhone()).replaceAll("\\s+", "");
        String address = input == null ? "" : clean(input.recipientAddress());
        if (name.isBlank() || name.length() > 64) {
            throw new BusinessException("INVALID_RECIPIENT_NAME", "请填写收货人，最多64个字符");
        }
        if (!phone.matches("(?:1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})")) {
            throw new BusinessException("INVALID_RECIPIENT_PHONE", "请填写有效的手机号或固定电话");
        }
        if (address.isBlank() || address.length() > 500) {
            throw new BusinessException("INVALID_RECIPIENT_ADDRESS", "请填写完整收货地址，最多500个字符");
        }
        return new RecipientInfo(name, phone, address);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
