package com.huohuo.scenicaiguide.tourist.dto.response;

import com.huohuo.scenicaiguide.digitalhuman.dto.response.DhProfileResponse;
import lombok.Data;

@Data
public class GuideSessionResponse {

    private Long sessionId;

    private String sessionNo;

    private Long scenicAreaId;

    private String status;

    private String welcomeText;

    private DhProfileResponse digitalHuman;

    private String authLevel;

    private boolean canRecommendRoute;

    private boolean canUseLiveGuide;
}
