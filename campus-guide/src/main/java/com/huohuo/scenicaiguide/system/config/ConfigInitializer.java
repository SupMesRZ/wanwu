package com.huohuo.scenicaiguide.system.config;

import com.huohuo.scenicaiguide.system.mapper.SysConfigMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * system 域配置初始化器
 *
 * <p>业务域配置默认值由各域自己的 Initializer 写入 sys_config，后台入口统一走 system 配置中心
 */
@Component
public class ConfigInitializer extends AbstractDomainConfigInitializer {

    private static final Map<String, String> DEFAULT_KEYS = new LinkedHashMap<>();

    static {
        DEFAULT_KEYS.put("ops-operation-log-enabled", "是否记录后台操作日志");
        DEFAULT_KEYS.put("ops-operation-log-retention-days", "操作日志保留天数，超期物理删除，默认 90");
        DEFAULT_KEYS.put("ops-operation-log-param-max-length", "单条日志请求参数最大长度（字符），默认 2000");
        DEFAULT_KEYS.put("ops-rate-limit-enabled", "是否启用接口限流");
        DEFAULT_KEYS.put("ops-rate-limit-rules", "限流规则 JSON 数组：name/pattern/methods/limit/windowSeconds/enabled");
    }

    private final AppConfigProperties appConfigProperties;

    @Autowired
    public ConfigInitializer(SysConfigMapper configMapper,
                             AppConfigProperties appConfigProperties,
                             ConfigService configService) {
        super(configMapper, configService);
        this.appConfigProperties = appConfigProperties;
    }

    @Override
    protected List<String> keyPrefixes() {
        return List.of("auth-", "ops-");
    }

    @Override
    protected Map<String, String> defaultKeys() {
        return DEFAULT_KEYS;
    }

    @Override
    protected String defaultValue(String key) {
        return appConfigProperties.get(key);
    }
}
