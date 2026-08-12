package com.lczz.common.exception;

public class BusinessException extends RuntimeException {
    private final String code;
    private final int status;

    public BusinessException(String code, String message) {
        this(400, code, message);
    }

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
