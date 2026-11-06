package com.huohuo.scenicaiguide.common.exception;

import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.name();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.name();
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
