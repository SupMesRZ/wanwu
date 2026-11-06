package com.huohuo.scenicaiguide.system.service;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.system.dto.response.ConfigDomainResponse;
import com.huohuo.scenicaiguide.system.dto.response.ConfigItemResponse;
import com.huohuo.scenicaiguide.system.entity.SysConfig;
import com.huohuo.scenicaiguide.system.security.AuthPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置管理服务
 *
 * <p>供管理后台调用，提供配置查询、更新、批量更新能力
 */
@Service
public class ConfigAdminService {

    private final ConfigService configService;

    @Autowired
    public ConfigAdminService(ConfigService configService) {
        this.configService = configService;
    }

    // system 域配置键前缀白名单
    private static final List<String> SYSTEM_KEY_PREFIXES = List.of("auth-", "ops-", "sms-", "jwt-", "system-");
    private static final Map<String, String> CONFIG_DOMAINS = Map.of(
        "system", "系统支撑",
        "scenic", "景区资产",
        "qa", "智能问答",
        "route", "路线导览",
        "tourist", "游客会话",
        "digitalhuman", "数字人交互",
        "analytics", "运营分析"
    );
    private static final Map<String, String> DOMAIN_KEY_PREFIXES = Map.of(
        "scenic", "scenic",
        "qa", "qa",
        "route", "route",
        "tourist", "tourist",
        "digitalhuman", "dh",
        "analytics", "analytics"
    );

    /**
     * 查询所有系统级配置（按分类分组排序）
     * <p>
     * 返回系统级与业务域配置，正式后台统一由 system 配置中心管理
     *
     * @return 配置列表
     */
    public List<ConfigItemResponse> listAll() {
        List<SysConfig> configs = configService.listAll();
        return configs.stream()
            .map(this::toResponse)
            .sorted(Comparator.comparing(ConfigItemResponse::getCategory)
                .thenComparing(ConfigItemResponse::getConfigKey))
            .collect(Collectors.toList());
    }

    /**
     * 查询所有配置域
     *
     * @return 配置域列表
     */
    public List<ConfigDomainResponse> listDomains() {
        return CONFIG_DOMAINS.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new ConfigDomainResponse(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * 查询指定配置域的配置
     *
     * @param domain 配置域
     * @return 配置列表
     */
    public List<ConfigItemResponse> listByDomain(String domain) {
        String normalizedDomain = normalizeDomain(domain);
        if ("system".equals(normalizedDomain)) {
            return configService.listAll().stream()
                .filter(this::isSystemConfig)
                .map(this::toResponse)
                .sorted(Comparator.comparing(ConfigItemResponse::getCategory)
                    .thenComparing(ConfigItemResponse::getConfigKey))
                .collect(Collectors.toList());
        }
        String keyPrefix = DOMAIN_KEY_PREFIXES.get(normalizedDomain);
        return configService.listByDomain(keyPrefix).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 判断配置是否属于 system 域（基于配置实体）
     *
     * @param config 配置实体
     * @return true 表示 system 域配置
     */
    private boolean isSystemConfig(SysConfig config) {
        if (config == null || config.getConfigKey() == null) {
            return false;
        }
        return isSystemConfigKey(config.getConfigKey());
    }

    /**
     * 判断配置键是否属于 system 域
     *
     * @param key 配置键
     * @return true 表示 system 域配置
     */
    private boolean isSystemConfigKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return SYSTEM_KEY_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    /**
     * 查询单个系统级配置
     *
     * @param key 配置键
     * @return 配置响应
     */
    private ConfigItemResponse get(String key) {
        SysConfig config = configService.getByKey(key);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toResponse(config);
    }

    /**
     * 查询指定配置域下的单个配置
     *
     * @param domain 配置域
     * @param key    配置键，可省略域前缀
     * @return 配置响应
     */
    public ConfigItemResponse getByDomain(String domain, String key) {
        String fullKey = resolveDomainKey(domain, key);
        return get(fullKey);
    }

    /**
     * 更新单个系统级配置
     *
     * @param key   配置键
     * @param value 配置值
     * @param desc  说明（可为 null）
     */
    private void update(String key, String value, String desc) {
        Long adminId = getCurrentAdminId();
        configService.set(key, value, desc, adminId);
    }

    /**
     * 更新指定配置域下的单个配置
     *
     * @param domain 配置域
     * @param key    配置键，可省略域前缀
     * @param value  配置值
     * @param desc   说明（可为 null）
     */
    public void updateByDomain(String domain, String key, String value, String desc) {
        update(resolveDomainKey(domain, key), value, desc);
    }

    /**
     * 批量更新系统级配置
     *
     * @param configs 配置键值对
     */
    private void batchUpdate(Map<String, String> configs) {
        Long adminId = getCurrentAdminId();
        configService.batchSet(configs, adminId);
    }

    /**
     * 批量更新指定配置域下的配置
     *
     * @param domain  配置域
     * @param configs 配置键值对，key 可省略域前缀
     */
    public void batchUpdateByDomain(String domain, Map<String, String> configs) {
        Map<String, String> normalized = configs.entrySet().stream()
            .collect(Collectors.toMap(
                e -> resolveDomainKey(domain, e.getKey()),
                Map.Entry::getValue));
        batchUpdate(normalized);
    }

    // ========== 私有方法 ==========

    private ConfigItemResponse toResponse(SysConfig config) {
        ConfigItemResponse resp = new ConfigItemResponse();
        resp.setConfigKey(config.getConfigKey());
        resp.setConfigValue(config.getConfigValue());
        resp.setDescription(config.getDescription());
        resp.setCategory(extractCategory(config.getConfigKey()));
        resp.setUpdatedAt(config.getUpdatedAt());
        resp.setUpdatedBy(config.getUpdatedBy());
        return resp;
    }

    /**
     * 从配置键提取分类
     * <p>
     * 如 "qa-vector-enabled" → "qa"
     * "ops-rate-limit-enabled" → "ops"
     *
     * @param key 配置键
     * @return 分类名
     */
    private String extractCategory(String key) {
        if (key == null || !key.contains("-")) {
            return "other";
        }
        return key.substring(0, key.indexOf('-'));
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "配置域不能为空");
        }
        String normalized = domain.trim().toLowerCase();
        if (!CONFIG_DOMAINS.containsKey(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的配置域：" + domain);
        }
        return normalized;
    }

    private String resolveDomainKey(String domain, String key) {
        String normalizedDomain = normalizeDomain(domain);
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "配置键不能为空");
        }
        String normalizedKey = key.trim().replace('.', '-').replace('_', '-');
        if ("system".equals(normalizedDomain)) {
            String lowerKey = normalizedKey.toLowerCase();
            if (!isSystemConfigKey(lowerKey)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "system 域不允许管理业务域配置键：" + key);
            }
            return normalizedKey;
        }
        String prefix = DOMAIN_KEY_PREFIXES.get(normalizedDomain) + "-";
        String lowerKey = normalizedKey.toLowerCase();
        return lowerKey.startsWith(prefix) ? normalizedKey : prefix + normalizedKey;
    }

    private Long getCurrentAdminId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.userId();
        }
        return null;
    }
}
