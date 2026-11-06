package com.huohuo.scenicaiguide.qa.config;

import com.huohuo.scenicaiguide.system.config.AbstractDomainConfigInitializer;
import com.huohuo.scenicaiguide.system.mapper.SysConfigMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * qa 域配置初始化器
 */
@Component
public class QaConfigInitializer extends AbstractDomainConfigInitializer {

    private static final Map<String, String> DEFAULT_KEYS = new LinkedHashMap<>();

    static {
        DEFAULT_KEYS.put("qa-rag-top-k", "RAG 召回文档数");
        DEFAULT_KEYS.put("qa-rag-similarity-threshold", "向量相似度阈值");
        DEFAULT_KEYS.put("qa-context-max-rounds", "上下文最大轮数");
        DEFAULT_KEYS.put("qa-model-think-directive", "关闭模型思考模式的指令，留空表示不下发");
        DEFAULT_KEYS.put("qa-vector-enabled", "是否启用向量检索增强（需可用的 Qdrant 与 embedding）");
        DEFAULT_KEYS.put("qa-vector-collection", "Qdrant 集合名");
        DEFAULT_KEYS.put("qa-vector-dimension", "向量维度（需与 embedding 模型一致）");
    }

    private final QaConfigProperties qaConfigProperties;

    @Autowired
    public QaConfigInitializer(SysConfigMapper configMapper,
                               QaConfigProperties qaConfigProperties,
                               ConfigService configService) {
        super(configMapper, configService);
        this.qaConfigProperties = qaConfigProperties;
    }

    @Override
    protected List<String> keyPrefixes() {
        return List.of("qa-");
    }

    @Override
    protected Map<String, String> defaultKeys() {
        return DEFAULT_KEYS;
    }

    @Override
    protected String defaultValue(String key) {
        return qaConfigProperties.get(key);
    }
}
