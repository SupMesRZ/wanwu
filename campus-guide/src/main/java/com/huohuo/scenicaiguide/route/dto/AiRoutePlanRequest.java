package com.huohuo.scenicaiguide.route.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRoutePlanRequest {

    private String routeName;

    private String startNodeKey;

    private String endNodeKey;

    private List<String> orderedSegmentIds = new ArrayList<>();

    private Integer targetDurationMinutes;

    private String reason;

    private Boolean alternative = false;
}
