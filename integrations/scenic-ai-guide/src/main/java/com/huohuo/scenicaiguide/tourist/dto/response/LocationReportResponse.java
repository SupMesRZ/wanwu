package com.huohuo.scenicaiguide.tourist.dto.response;

import lombok.Data;

@Data
public class LocationReportResponse {

    private boolean accepted;

    private String state;

    private String triggeredSpotId;

    private String triggeredSpotName;

    private GuideMessageResponse guideMessage;

    private RouteProgressResponse routeProgress;
}
