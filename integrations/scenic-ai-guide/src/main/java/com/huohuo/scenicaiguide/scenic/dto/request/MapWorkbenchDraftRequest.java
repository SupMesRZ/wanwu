package com.huohuo.scenicaiguide.scenic.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class MapWorkbenchDraftRequest {

    private List<BigDecimal> mapCenter;

    private Integer defaultZoom;

    private List<SpotDraft> spots = new ArrayList<>();

    private List<FacilityDraft> facilities = new ArrayList<>();

    private List<EndpointDraft> routeEndpoints = new ArrayList<>();

    private List<RouteDraft> routeSegments = new ArrayList<>();

    private List<PresetRouteDraft> presetRoutes = new ArrayList<>();

    @Data
    public static class SpotDraft {
        private String id;
        private String name;
        private String description;
        /** 标签 code 列表，引用全局词表，展示时按 code 回填名称 */
        private List<String> tags;
        private List<BigDecimal> labelPoint;
        private List<List<BigDecimal>> polygon;
        private String fenceType;
        private Integer radius;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer status;
    }

    @Data
    public static class FacilityDraft {
        private String id;
        private String name;
        private String facilityType;
        private String description;
        private List<BigDecimal> point;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer status;
    }

    @Data
    public static class EndpointDraft {
        private String id;
        private String name;
        private String endpointType;
        private List<BigDecimal> point;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer status;
    }

    @Data
    public static class RouteDraft {
        private String id;
        private String name;
        private EndpointRefDraft from;
        private EndpointRefDraft to;
        private List<List<BigDecimal>> middlePoints = new ArrayList<>();
        private Integer lengthMeter;
        private Integer durationMin;
        private String routeType;
        private Boolean paid;
        private String aiNote;
        private Integer crowdLevel;
        private Integer status;
    }

    @Data
    public static class PresetRouteDraft {
        private String id;
        private String name;
        private List<String> spotIds = new ArrayList<>();
        private List<String> segmentIds = new ArrayList<>();
        private String description;
        private Integer durationMin;
        private String audience;
        private String recommendationNote;
        private Integer status;
    }

    @Data
    public static class EndpointRefDraft {
        private String type;
        private String id;
        private String name;
        private List<BigDecimal> point;
        private BigDecimal longitude;
        private BigDecimal latitude;
    }
}
