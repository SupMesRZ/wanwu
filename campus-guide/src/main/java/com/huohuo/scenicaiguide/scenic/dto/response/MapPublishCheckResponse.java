package com.huohuo.scenicaiguide.scenic.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MapPublishCheckResponse {

    private Long draftId;

    private String draftName;

    private String status;

    private Integer score;

    private Boolean publishable;

    private Integer blockerCount;

    private Integer warningCount;

    private Integer spotCount;

    private Integer facilityCount;

    private Integer routeSegmentCount;

    private Integer presetRouteCount;

    private Integer enabledPresetRouteCount;

    private LocalDateTime checkedAt;

    private List<Issue> issues = new ArrayList<>();

    @Data
    public static class Issue {

        private String level;

        private String code;

        private String title;

        private String detail;
    }
}
