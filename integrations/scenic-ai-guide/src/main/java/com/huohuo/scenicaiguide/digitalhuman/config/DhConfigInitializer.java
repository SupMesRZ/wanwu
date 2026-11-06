package com.huohuo.scenicaiguide.digitalhuman.config;

import com.huohuo.scenicaiguide.system.config.AbstractDomainConfigInitializer;
import com.huohuo.scenicaiguide.system.mapper.SysConfigMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * digitalhuman 域配置初始化器
 */
@Component
public class DhConfigInitializer extends AbstractDomainConfigInitializer {

    private static final Map<String, String> DEFAULT_KEYS = new LinkedHashMap<>();

    static {
        DEFAULT_KEYS.put("dh-tts-cache-ttl-hours", "TTS 缓存有效期（小时）");
        DEFAULT_KEYS.put("dh-tts-provider", "TTS 供应商（aliyun / mock，mock 生成静音 WAV 供无密钥演示）");
        DEFAULT_KEYS.put("dh-asr-provider", "ASR 供应商（aliyun / mock，mock 返回空识别结果供无密钥演示）");
    }

    private final DhConfigProperties dhConfigProperties;

    @Autowired
    public DhConfigInitializer(SysConfigMapper configMapper,
                                DhConfigProperties dhConfigProperties,
                                ConfigService configService) {
        super(configMapper, configService);
        this.dhConfigProperties = dhConfigProperties;
    }

    @Override
    protected List<String> keyPrefixes() {
        return List.of("dh-");
    }

    @Override
    protected Map<String, String> defaultKeys() {
        return DEFAULT_KEYS;
    }

    @Override
    protected String defaultValue(String key) {
        return dhConfigProperties.get(key);
    }
}
