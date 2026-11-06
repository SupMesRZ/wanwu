package com.huohuo.scenicaiguide.system.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huohuo.scenicaiguide.system.entity.SysConfig;
import com.huohuo.scenicaiguide.system.mapper.SysConfigMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 域配置初始化器基类
 *
 * <p>按 CLAUDE.md 域职责，配置的存储、缓存与后台入口由 system 域统一提供，
 * 各业务域只负责登记自己的配置键、说明和 yaml 兜底值
 *
 * <p><b>逐键补齐而非整体播种</b>：早期各域各自实现，且都以"库中已有任意本域前缀的键"
 * 作为跳过条件，导致后续版本新增的配置项在已有环境永远种不进去，只能靠代码默认值兜底。
 * system 域自身的实现更严重——判断条件是整表非空，任一业务域先启动就会让 auth/ops 配置全部落空
 */
public abstract class AbstractDomainConfigInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDomainConfigInitializer.class);

    private final SysConfigMapper configMapper;
    private final ConfigService configService;

    protected AbstractDomainConfigInitializer(SysConfigMapper configMapper, ConfigService configService) {
        this.configMapper = configMapper;
        this.configService = configService;
    }

    /**
     * 本域配置键前缀，用于启动时预热 Redis
     */
    protected abstract List<String> keyPrefixes();

    /**
     * 配置键与说明，新增配置项在此登记即可
     */
    protected abstract Map<String, String> defaultKeys();

    /**
     * 从本域 yaml 取兜底值，返回 null 表示该键不播种
     */
    protected abstract String defaultValue(String key);

    @Override
    public void run(String... args) {
        try {
            defaultKeys().forEach(this::insertIfAbsent);
            warmUpRedis();
        } catch (Exception e) {
            // 启动期数据库或 Redis 不可用时不阻断应用，运行时回落到代码默认值
            LOGGER.warn("{} 配置初始化跳过：{}", getClass().getSimpleName(), e.getMessage());
        }
    }

    private void insertIfAbsent(String key, String description) {
        String value = defaultValue(key);
        if (value == null) {
            return;
        }
        Long existing = configMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigKey, key)
            .eq(SysConfig::getDeleted, 0));
        if (existing != null && existing > 0) {
            return;
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(config);
    }

    private void warmUpRedis() {
        for (String prefix : keyPrefixes()) {
            for (SysConfig config : configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .likeRight(SysConfig::getConfigKey, prefix)
                .eq(SysConfig::getDeleted, 0))) {
                if (config.getConfigValue() != null) {
                    configService.set(config.getConfigKey(), config.getConfigValue(), null, null);
                }
            }
        }
    }
}
