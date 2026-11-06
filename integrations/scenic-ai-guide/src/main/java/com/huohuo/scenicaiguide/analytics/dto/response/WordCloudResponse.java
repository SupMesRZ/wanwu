package com.huohuo.scenicaiguide.analytics.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 游客提问词云
 */
@Data
public class WordCloudResponse {

    private String range;

    private int messageCount;

    private List<WordItem> words = new ArrayList<>();

    @Data
    public static class WordItem {

        private String text;

        private int weight;

        /** SPOT/FACILITY/TAG 为领域词，NGRAM 为从原文提取的热词 */
        private String source;
    }
}
