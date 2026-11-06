package com.huohuo.scenicaiguide.digitalhuman.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class DhTtsOptionResponse {

    private List<OptionItem> voices;

    private List<String> outputFormats;

    private List<Integer> sampleRates;

    private Range speechRate;

    private Range pitchRate;

    private Range volume;

    @Data
    @AllArgsConstructor
    public static class OptionItem {
        private String label;
        private String value;
    }

    @Data
    @AllArgsConstructor
    public static class Range {
        private Integer min;
        private Integer max;
        private Integer defaultValue;
    }
}
