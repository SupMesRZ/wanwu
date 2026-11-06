package com.huohuo.scenicaiguide.digitalhuman.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * ASR 识别结果
 */
@Data
@AllArgsConstructor
public class AsrResponse {

    /**
     * 识别文本，空串表示没有听清
     */
    private String text;
}
