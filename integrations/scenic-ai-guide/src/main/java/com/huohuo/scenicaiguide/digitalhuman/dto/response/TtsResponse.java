package com.huohuo.scenicaiguide.digitalhuman.dto.response;

import lombok.Data;

@Data
public class TtsResponse {

    private String audioUrl;

    private String audioBase64;

    private String format;

    private String voiceCode;

    private boolean cached;
}
