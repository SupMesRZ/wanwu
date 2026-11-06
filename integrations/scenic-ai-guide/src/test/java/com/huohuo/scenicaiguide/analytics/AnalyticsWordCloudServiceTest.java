package com.huohuo.scenicaiguide.analytics;

import com.huohuo.scenicaiguide.analytics.dto.response.WordCloudResponse;
import com.huohuo.scenicaiguide.analytics.service.AnalyticsWordCloudService;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTagService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsWordCloudServiceTest {

    @Test
    void domainWordsAreCountedWithSources() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        payload.getSpots().add(spot("s1", "书院"));
        payload.getSpots().add(spot("s2", "茶亭"));
        payload.getFacilities().add(facility("f1", "卫生间", "卫生间"));
        TestFixture fixture = new TestFixture(payload, Map.of("food", "美食"));
        fixture.stubMessages(3L, "书院怎么走", "书院附近有卫生间吗", "想吃点美食");

        WordCloudResponse cloud = fixture.service.wordCloud("today", null, null);

        assertThat(cloud.getMessageCount()).isEqualTo(3);
        assertThat(word(cloud, "书院")).satisfies(item -> {
            assertThat(item.getWeight()).isEqualTo(2);
            assertThat(item.getSource()).isEqualTo("SPOT");
        });
        assertThat(word(cloud, "卫生间").getSource()).isEqualTo("FACILITY");
        assertThat(word(cloud, "美食").getSource()).isEqualTo("TAG");
        // 没人问过茶亭，零次领域词不上榜
        assertThat(word(cloud, "茶亭")).isNull();
    }

    @Test
    void repeatedBigramSurfacesAsNgram() {
        TestFixture fixture = new TestFixture(null, Map.of());
        fixture.stubMessages(3L, "夜景真美", "夜景迷人", "夜景");

        WordCloudResponse cloud = fixture.service.wordCloud("week", null, null);

        assertThat(word(cloud, "夜景")).satisfies(item -> {
            assertThat(item.getWeight()).isEqualTo(3);
            assertThat(item.getSource()).isEqualTo("NGRAM");
        });
        // 只出现一次的二元组是噪声，不上榜
        assertThat(word(cloud, "真美")).isNull();
        assertThat(word(cloud, "迷人")).isNull();
    }

    @Test
    void stopCharBigramsAreFiltered() {
        TestFixture fixture = new TestFixture(null, Map.of());
        fixture.stubMessages(2L, "好的好的", "好的呢");

        WordCloudResponse cloud = fixture.service.wordCloud("today", null, null);

        assertThat(cloud.getWords()).isEmpty();
    }

    @Test
    void bigramInsideVocabularyIsDeduped() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        payload.getSpots().add(spot("s1", "云山书院"));
        TestFixture fixture = new TestFixture(payload, Map.of());
        fixture.stubMessages(2L, "云山书院好玩吗", "云山书院");

        WordCloudResponse cloud = fixture.service.wordCloud("today", null, null);

        // 书院/云山/山书都是领域词的子串，不能再以 NGRAM 身份重复上榜
        assertThat(cloud.getWords()).hasSize(1);
        assertThat(word(cloud, "云山书院").getWeight()).isEqualTo(2);
    }

    @Test
    void emptySampleReturnsNoWords() {
        TestFixture fixture = new TestFixture(null, Map.of());
        fixture.stubMessages(0L);

        WordCloudResponse cloud = fixture.service.wordCloud("today", null, null);

        assertThat(cloud.getMessageCount()).isZero();
        assertThat(cloud.getWords()).isEmpty();
        assertThat(cloud.getRange()).isEqualTo("today");
    }

    @Test
    void topologyFailureDegradesToBigramsOnly() {
        TouristMessageMapper messageMapper = mock(TouristMessageMapper.class);
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTagService tagService = mock(ScenicTagService.class);
        when(topologyService.getPublishedTopology(any())).thenThrow(new RuntimeException("草稿损坏"));
        when(tagService.enabledCodeToName()).thenThrow(new RuntimeException("标签服务故障"));
        AnalyticsWordCloudService service = new AnalyticsWordCloudService(messageMapper, topologyService, tagService);
        when(messageMapper.selectCount(any())).thenReturn(2L);
        when(messageMapper.selectMaps(any())).thenReturn(List.of(row("夜景夜景"), row("夜景")));

        WordCloudResponse cloud = service.wordCloud("today", null, null);

        // 领域词表加载失败只损失 SPOT/FACILITY/TAG 层，词云本身不能挂
        assertThat(word(cloud, "夜景").getSource()).isEqualTo("NGRAM");
    }

    @Test
    void wordsAreCappedAtThirty() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        String[] suffixes = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
        String[] messages = new String[35];
        for (int index = 0; index < 35; index++) {
            String name = "设施" + suffixes[index % 10] + index;
            payload.getFacilities().add(facility("f" + index, name, null));
            // 每个词放独立消息，避免拼接后跨界二元组干扰断言
            messages[index] = name;
        }
        TestFixture fixture = new TestFixture(payload, Map.of());
        fixture.stubMessages(35L, messages);

        WordCloudResponse cloud = fixture.service.wordCloud("month", null, null);

        assertThat(cloud.getWords()).hasSize(30);
        assertThat(cloud.getWords()).allMatch(item -> "FACILITY".equals(item.getSource()));
    }

    private static WordCloudResponse.WordItem word(WordCloudResponse cloud, String text) {
        return cloud.getWords().stream()
            .filter(item -> item.getText().equals(text))
            .findFirst()
            .orElse(null);
    }

    private static MapWorkbenchDraftRequest.SpotDraft spot(String id, String name) {
        MapWorkbenchDraftRequest.SpotDraft spot = new MapWorkbenchDraftRequest.SpotDraft();
        spot.setId(id);
        spot.setName(name);
        spot.setStatus(1);
        return spot;
    }

    private static MapWorkbenchDraftRequest.FacilityDraft facility(String id, String name, String type) {
        MapWorkbenchDraftRequest.FacilityDraft facility = new MapWorkbenchDraftRequest.FacilityDraft();
        facility.setId(id);
        facility.setName(name);
        facility.setFacilityType(type);
        facility.setStatus(1);
        return facility;
    }

    private static Map<String, Object> row(String content) {
        Map<String, Object> row = new HashMap<>();
        row.put("content", content);
        return row;
    }

    private static class TestFixture {
        final AnalyticsWordCloudService service;
        private final TouristMessageMapper messageMapper;

        TestFixture(MapWorkbenchDraftRequest payload, Map<String, String> tags) {
            messageMapper = mock(TouristMessageMapper.class);
            ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
            ScenicTagService tagService = mock(ScenicTagService.class);
            if (payload == null) {
                when(topologyService.getPublishedTopology(any())).thenReturn(null);
            } else {
                ScenicTopologyService.PublishedTopology topology =
                    new ScenicTopologyService.PublishedTopology(1L, 1L, payload);
                when(topologyService.getPublishedTopology(any())).thenReturn(topology);
                when(topologyService.enabledSpots(topology)).thenReturn(payload.getSpots());
            }
            when(tagService.enabledCodeToName()).thenReturn(tags);
            service = new AnalyticsWordCloudService(messageMapper, topologyService, tagService);
        }

        void stubMessages(long count, String... contents) {
            when(messageMapper.selectCount(any())).thenReturn(count);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String content : contents) {
                rows.add(row(content));
            }
            when(messageMapper.selectMaps(any())).thenReturn(rows);
        }
    }
}
