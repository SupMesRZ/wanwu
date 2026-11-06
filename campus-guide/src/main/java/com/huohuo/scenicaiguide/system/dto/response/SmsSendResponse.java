package com.huohuo.scenicaiguide.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmsSendResponse {

    /** 验证码回显（定档：不接真实短信，前端直接展示） */
    private String displaySmsCode;
}
