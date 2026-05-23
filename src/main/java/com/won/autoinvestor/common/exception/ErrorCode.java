package com.won.autoinvestor.common.exception;

public enum ErrorCode {

    NO_DATA("E001", "데이터가 존재하지 않습니다"),
    INVALID_REQUEST("E002", "잘못된 요청입니다");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
