package com.huohuo.scenicaiguide.tourist.dto.response;

import com.huohuo.scenicaiguide.route.dto.AiRouteResponse;
import lombok.Data;

@Data
public class GuideMessageResponse {

    private String answer;

    private String messageType;

    private String actionState;

    private String audioUrl;

    private String audioBase64;

    private String audioStatus;

    private String audioError;

    private String evidence;

    private AiRouteResponse route;
}
