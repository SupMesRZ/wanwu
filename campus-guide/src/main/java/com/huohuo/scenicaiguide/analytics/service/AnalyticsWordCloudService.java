package com.huohuo.scenicaiguide.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huohuo.scenicaiguide.analytics.dto.response.WordCloudResponse;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTagService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import com.huohuo.scenicaiguide.tourist.entity.TouristMessage;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 游客提问词云。算法分两层：
 * 领域词表（景点/设施/标签）逐条计数，运营最关心"游客在问哪些点"；
 * 中文二元组滑窗补充词表之外的热词，靠停用字和最低频次压住噪声。
 * 不引入 NLP 分词依赖——样本是短口语问句，词表锚定已经覆盖主要信号
 */
@Service
public class AnalyticsWordCloudService {

    private static final Long DEFAULT_SCENIC_AREA_ID = 1L;
    private static final int MAX_SAMPLE_MESSAGES = 5000;
    private static final int MIN_BIGRAM_FREQUENCY = 2;
    private static final int MAX_WORDS = 30;
    private static final String STOP_CHARS =
        "的了是在我有和你他她它这那也不吗呢吧啊哦呀很太就还要会能去到上说好个把从与及或但如果因于对其等中里问谁多什";

    private final TouristMessageMapper messageMapper;
    private final ScenicTopologyService topologyService;
    private final ScenicTagService tagService;

    public AnalyticsWordCloudService(TouristMessageMapper messageMapper,
                                     ScenicTopologyService topologyService,
                                     ScenicTagService tagService) {
        this.messageMapper = messageMapper;
        this.topologyService = topologyService;
        this.tagService = tagService;
    }

    @Transactional(readOnly = true)
    public WordCloudResponse wordCloud(String range, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(range, startDate, endDate);
        LocalDateTime startAt = dateRange.start().atStartOfDay();
        LocalDateTime endAt = dateRange.end().atTime(LocalTime.MAX);

        WordCloudResponse response = new WordCloudResponse();
        response.setRange(dateRange.label());
        response.setMessageCount(messageMapper.selectCount(new QueryWrapper<TouristMessage>()
            .eq("sender", "TOURIST")
            .between("created_at", startAt, endAt)).intValue());
        List<String> contents = messageMapper.selectMaps(new QueryWrapper<TouristMessage>()
                .select("content")
                .eq("sender", "TOURIST")
                .between("created_at", startAt, endAt)
                .orderByDesc("id")
                .last("LIMIT " + MAX_SAMPLE_MESSAGES))
            .stream()
            .map(row -> Objects.toString(row.get("content"), ""))
            .filter(content -> !content.isBlank())
            .toList();
        if (contents.isEmpty()) {
            return response;
        }

        Map<String, WordCloudResponse.WordItem> words = new LinkedHashMap<>();
        List<VocabularyWord> vocabulary = domainVocabulary();
        for (VocabularyWord word : vocabulary) {
            int count = contents.stream().mapToInt(content -> occurrences(content, word.text())).sum();
            if (count > 0) {
                words.put(word.text(), item(word.text(), count, word.source()));
            }
        }
        collectBigrams(contents, vocabulary, words);

        response.setWords(words.values().stream()
            .sorted((left, right) -> Integer.compare(right.getWeight(), left.getWeight()))
            .limit(MAX_WORDS)
            .toList());
        return response;
    }

    /**
     * 已发布地图里的领域词表。读不到地图或词表服务故障时降级为空，
     * 词云退化为纯二元组统计，不弄坏只读分析接口
     */
    private List<VocabularyWord> domainVocabulary() {
        List<VocabularyWord> words = new ArrayList<>();
        try {
            ScenicTopologyService.PublishedTopology topology =
                topologyService.getPublishedTopology(DEFAULT_SCENIC_AREA_ID);
            if (topology != null) {
                topologyService.enabledSpots(topology).stream()
                    .map(MapWorkbenchDraftRequest.SpotDraft::getName)
                    .filter(name -> name != null && name.length() >= 2)
                    .forEach(name -> words.add(new VocabularyWord(name, "SPOT")));
                topology.payload().getFacilities().stream()
                    .filter(Objects::nonNull)
                    .filter(facility -> facility.getStatus() == null || facility.getStatus() == 1)
                    .forEach(facility -> {
                        if (facility.getName() != null && facility.getName().length() >= 2) {
                            words.add(new VocabularyWord(facility.getName(), "FACILITY"));
                        }
                        if (facility.getFacilityType() != null && facility.getFacilityType().length() >= 2) {
                            words.add(new VocabularyWord(facility.getFacilityType(), "FACILITY"));
                        }
                    });
            }
        } catch (Exception ignored) {
        }
        try {
            tagService.enabledCodeToName().values().stream()
                .filter(name -> name != null && name.length() >= 2)
                .forEach(name -> words.add(new VocabularyWord(name, "TAG")));
        } catch (Exception ignored) {
        }
        return words.stream().distinct().toList();
    }

    private void collectBigrams(List<String> contents,
                                List<VocabularyWord> vocabulary,
                                Map<String, WordCloudResponse.WordItem> words) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String content : contents) {
            String han = content.replaceAll("[^\\u4e00-\\u9fff]", "");
            for (int index = 0; index < han.length() - 1; index++) {
                String bigram = han.substring(index, index + 2);
                if (STOP_CHARS.indexOf(bigram.charAt(0)) >= 0 || STOP_CHARS.indexOf(bigram.charAt(1)) >= 0) {
                    continue;
                }
                if (bigram.charAt(0) == bigram.charAt(1)) {
                    continue;
                }
                String captured = bigram;
                boolean insideVocabulary = vocabulary.stream()
                    .anyMatch(word -> word.text().contains(captured));
                if (insideVocabulary) {
                    continue;
                }
                frequencies.merge(bigram, 1, Integer::sum);
            }
        }
        frequencies.forEach((bigram, count) -> {
            if (count >= MIN_BIGRAM_FREQUENCY && !words.containsKey(bigram)) {
                words.put(bigram, item(bigram, count, "NGRAM"));
            }
        });
    }

    private int occurrences(String content, String word) {
        int count = 0;
        int index = content.indexOf(word);
        while (index >= 0) {
            count++;
            index = content.indexOf(word, index + word.length());
        }
        return count;
    }

    private WordCloudResponse.WordItem item(String text, int weight, String source) {
        WordCloudResponse.WordItem item = new WordCloudResponse.WordItem();
        item.setText(text);
        item.setWeight(weight);
        item.setSource(source);
        return item;
    }

    private DateRange resolveDateRange(String range, LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        String normalized = range == null ? "today" : range.toLowerCase(Locale.ROOT);
        if ("custom".equals(normalized) && startDate != null && endDate != null && !startDate.isAfter(endDate)) {
            return new DateRange("custom", startDate, endDate);
        }
        if ("month".equals(normalized)) {
            return new DateRange("month", today.minusDays(29), today);
        }
        if ("week".equals(normalized)) {
            return new DateRange("week", today.minusDays(6), today);
        }
        return new DateRange("today", today, today);
    }

    private record VocabularyWord(String text, String source) {
    }

    private record DateRange(String label, LocalDate start, LocalDate end) {
    }
}
