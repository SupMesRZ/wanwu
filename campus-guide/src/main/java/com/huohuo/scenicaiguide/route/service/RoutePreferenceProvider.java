package com.huohuo.scenicaiguide.route.service;

import com.huohuo.scenicaiguide.scenic.service.ScenicTagService;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * AI 路线偏好提供者
 * <p>
 * 读取 {@code route-ai-prefer-tags}（逗号分隔的标签 code），经词表解析成中文名，
 * 供算路子 agent 在选线时优先途经带这些标签的景点
 */
@Service
public class RoutePreferenceProvider {

    private static final String KEY_PREFER_TAGS = "route-ai-prefer-tags";

    private final ConfigService configService;
    private final ScenicTagService tagService;

    public RoutePreferenceProvider(ConfigService configService, ScenicTagService tagService) {
        this.configService = configService;
        this.tagService = tagService;
    }

    /**
     * 当前偏好标签的中文名列表。配置为空或词表故障都返回空——
     * 偏好是选线的软提示，缺了不该影响路线功能本身
     */
    public List<String> preferredTagNames() {
        String raw;
        try {
            raw = configService.getString(KEY_PREFER_TAGS, "");
        } catch (Exception e) {
            return List.of();
        }
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> codes = Arrays.stream(raw.split("[,，、\\s]+"))
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .toList();
        try {
            return tagService.resolveNames(codes);
        } catch (Exception e) {
            return List.of();
        }
    }
}
