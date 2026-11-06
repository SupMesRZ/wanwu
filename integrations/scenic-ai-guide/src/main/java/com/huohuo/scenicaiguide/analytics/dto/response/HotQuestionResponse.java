package com.huohuo.scenicaiguide.analytics.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotQuestionResponse {

    private Integer rank;

    private String question;

    private Long count;

    private String source;

    private LocalDateTime latestAt;
}
